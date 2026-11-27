package mn.icode.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.OrderApiController;
import mn.icode.dto.OrderDetailResponse;
import mn.icode.dto.OrderItemSummary;
import mn.icode.dto.OrderResponse;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;
import mn.icode.service.OrderService;

@WebMvcTest(controllers = OrderApiController.class)
@Import({ SecurityConfig.class, ServletWebSecurityAutoConfiguration.class })
class OrderApiControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private OrderService orderService;

        @MockitoBean
        private ProductRepository productRepository;

        @MockitoBean
        private OrderRepository orderRepository;

        @MockitoBean
        private UserRepository userRepository;

        @Test
        void listMyOrders_unauthenticated_redirectsToLogin() throws Exception {
                mockMvc.perform(MockMvcRequestBuilders.get("/api/orders"))
                                .andExpect(status().is3xxRedirection());
        }

        @Test
        void listMyOrders_authenticatedCustomer_returnsPaginatedOrders() throws Exception {
                OrderItemSummary item = new OrderItemSummary(
                                10L, "Immutable Book Title", new BigDecimal("25.00"), 2, new BigDecimal("50.00"));
                OrderResponse order = new OrderResponse(
                                100L, Instant.parse("2026-11-27T10:00:00Z"), "PENDING", new BigDecimal("50.00"), 1,
                                List.of(item));

                when(orderService.getCurrentCustomerOrders(anyInt(), anyInt()))
                                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

                mockMvc.perform(MockMvcRequestBuilders.get("/api/orders")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].id").value(100))
                                .andExpect(jsonPath("$.content[0].items[0].productName").value("Immutable Book Title"));
        }

        @Test
        void getMyOrder_ownedOrder_returnsOrderDetail() throws Exception {
                OrderItemSummary item = new OrderItemSummary(
                                10L, "Snapshot Title", new BigDecimal("15.00"), 1, new BigDecimal("15.00"));
                OrderDetailResponse detail = new OrderDetailResponse(
                                5L,
                                Instant.parse("2026-11-27T12:00:00Z"),
                                "PENDING",
                                "Home",
                                "Ulaanbaatar",
                                "Sukhbaatar",
                                "Peace Ave 1",
                                "99112233",
                                new BigDecimal("15.00"),
                                new BigDecimal("15.00"),
                                1,
                                List.of(item));

                when(orderService.getCurrentCustomerOrderDetail(5L)).thenReturn(detail);

                mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/5")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(5))
                                .andExpect(jsonPath("$.items[0].productName").value("Snapshot Title"))
                                .andExpect(jsonPath("$.cancellable").value(true));
        }

        @Test
        void getMyOrder_foreignOrder_returnsNotFound() throws Exception {
                when(orderService.getCurrentCustomerOrderDetail(999L))
                                .thenThrow(new ResourceNotFoundException("Order not found"));

                mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/999")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isNotFound());
        }

        @Test
        void cancelOrder_ownedEligibleOrder_returnsNoContent() throws Exception {

                mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/5/cancel")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .with(csrf()))
                                .andExpect(status().isNoContent());

                verify(orderService).cancelOrder(5L);
        }

        @Test
        void cancelOrder_ineligibleOrRepeatedOrder_returnsConflict() throws Exception {

                doThrow(new ConflictException(
                                "Order cannot be cancelled in its current status"))
                                .when(orderService).cancelOrder(5L);

                mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/5/cancel")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .with(csrf()))
                                .andExpect(status().isConflict());

                verify(orderService).cancelOrder(5L);
        }

        @Test
        void cancelOrder_foreignOrMissingOrder_returnsNotFound() throws Exception {

                doThrow(new ResourceNotFoundException("Order not found"))
                                .when(orderService).cancelOrder(999L);

                mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/999/cancel")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .with(csrf()))
                                .andExpect(status().isNotFound());

                verify(orderService).cancelOrder(999L);
        }

        @Test
        void cancelOrder_adminUser_returnsForbidden() throws Exception {

                mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/5/cancel")
                                .with(user("admin@example.com").roles("ADMIN"))
                                .with(csrf()))
                                .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
        }

        @Test
        void cancelOrder_unauthenticated_redirectsToLogin() throws Exception {

                mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/5/cancel")
                                .with(csrf()))
                                .andExpect(status().is3xxRedirection());

                verifyNoInteractions(orderService);
        }

        @Test
        void cancelOrder_missingCsrfToken_returnsForbidden() throws Exception {

                mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/5/cancel")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
        }
}