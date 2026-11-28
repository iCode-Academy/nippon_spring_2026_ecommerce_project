package mn.icode.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.PageController;
import mn.icode.dto.OrderDetailResponse;
import mn.icode.dto.OrderItemSummary;
import mn.icode.dto.OrderResponse;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.OrderStatus;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;
import mn.icode.service.CartService;
import mn.icode.service.OrderService;
import mn.icode.service.UserService;

@WebMvcTest(controllers = PageController.class)
@Import({ SecurityConfig.class, ServletWebSecurityAutoConfiguration.class })
class OrderPageControllerTest {

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

        @MockitoBean
        private UserService userService;

        @MockitoBean
        private CartService cartService;

        private OrderDetailResponse createOrderDetail(OrderStatus status) {

                OrderItemSummary item = new OrderItemSummary(
                                10L,
                                "Immutable Spring Guide",
                                new BigDecimal("20.00"),
                                2,
                                new BigDecimal("40.00"));

                return new OrderDetailResponse(
                                7L,
                                Instant.parse("2026-11-27T12:00:00Z"),
                                status.name(),
                                "Home",
                                "Ulaanbaatar",
                                "Sukhbaatar",
                                "Peace Ave 1",
                                "99112233",
                                new BigDecimal("40.00"),
                                new BigDecimal("40.00"),
                                1,
                                List.of(item));
        }

        @Test
        void orderHistory_unauthenticated_redirectsToLogin() throws Exception {
                mockMvc.perform(MockMvcRequestBuilders.get("/orders"))
                                .andExpect(status().is3xxRedirection());
        }

        @Test
        void orderHistory_emptyOrders_showsEmptyState() throws Exception {
                when(orderService.getCurrentCustomerOrders(anyInt(), anyInt()))
                                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

                mockMvc.perform(MockMvcRequestBuilders.get("/orders")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("orders"))
                                .andExpect(model().attributeExists("orders"))
                                .andExpect(content().string(containsString("aria-label=\"Account navigation\"")))
                                .andExpect(content().string(containsString("href=\"/addresses\"")))
                                .andExpect(content().string(containsString("No orders yet")));
        }

        @Test
        void orderHistory_withOrders_rendersOrderList() throws Exception {
                OrderItemSummary item = new OrderItemSummary(
                                10L, "Snapshot Java Book", new BigDecimal("30.00"), 1, new BigDecimal("30.00"));
                OrderResponse order = new OrderResponse(
                                42L, Instant.parse("2026-11-27T10:00:00Z"), "PENDING", new BigDecimal("30.00"), 1,
                                List.of(item));

                when(orderService.getCurrentCustomerOrders(anyInt(), anyInt()))
                                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

                mockMvc.perform(MockMvcRequestBuilders.get("/orders")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("orders"))
                                .andExpect(model().attributeExists("orders"));
        }

        @Test
        void orderDetail_ownedOrder_rendersDetailView() throws Exception {
                OrderItemSummary item = new OrderItemSummary(
                                10L, "Immutable Spring Guide", new BigDecimal("20.00"), 2, new BigDecimal("40.00"));
                OrderDetailResponse detail = new OrderDetailResponse(
                                7L,
                                Instant.parse("2026-11-27T12:00:00Z"),
                                "PENDING",
                                "Home",
                                "Ulaanbaatar",
                                "Sukhbaatar",
                                "Peace Ave 1",
                                "99112233",
                                new BigDecimal("40.00"),
                                new BigDecimal("40.00"),
                                1,
                                List.of(item));

                when(orderService.getCurrentCustomerOrderDetail(7L)).thenReturn(detail);

                mockMvc.perform(MockMvcRequestBuilders.get("/orders/7")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("order-detail"))
                                .andExpect(model().attributeExists("detail"))
                                .andExpect(content().string(containsString("aria-label=\"Account navigation\"")))
                                .andExpect(content().string(containsString("aria-current=\"page\"")))
                                .andExpect(content().string(containsString("Immutable Spring Guide")));
        }

        @Test
        void orderDetail_foreignOrMissingOrder_returns404View() throws Exception {
                when(orderService.getCurrentCustomerOrderDetail(999L))
                                .thenThrow(new ResourceNotFoundException("Order not found"));

                mockMvc.perform(MockMvcRequestBuilders.get("/orders/999")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("error/404"));
        }

        @Test
        void cancelOrder_success_redirectsWithSuccessMessage() throws Exception {

                mockMvc.perform(MockMvcRequestBuilders.post("/orders/7/cancel")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .with(csrf()))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/orders/7"))
                                .andExpect(flash().attribute(
                                                "flashSuccess",
                                                "Order cancelled successfully."));

                verify(orderService).cancelOrder(7L);
        }

        @Test
        void cancelOrder_conflict_redirectsWithErrorMessage() throws Exception {

                String message = "Order cannot be cancelled in its current status";

                doThrow(new ConflictException(message))
                                .when(orderService).cancelOrder(7L);

                mockMvc.perform(MockMvcRequestBuilders.post("/orders/7/cancel")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .with(csrf()))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/orders/7"))
                                .andExpect(flash().attribute("flashError", message));

                verify(orderService).cancelOrder(7L);
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = { "PENDING", "CONFIRMED" })
        void orderDetail_eligibleOrder_showsCancelButton(
                        OrderStatus orderStatus) throws Exception {

                when(orderService.getCurrentCustomerOrderDetail(7L))
                                .thenReturn(createOrderDetail(orderStatus));

                mockMvc.perform(MockMvcRequestBuilders.get("/orders/7")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("order-detail"))
                                .andExpect(content().string(
                                                containsString("Cancel Order")))
                                .andExpect(content().string(
                                                containsString("action=\"/orders/7/cancel\"")))
                                .andExpect(content().string(
                                                containsString("name=\"_csrf\"")));
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = { "SHIPPED", "DELIVERED", "CANCELLED" })
        void orderDetail_ineligibleOrder_hidesCancelButton(
                        OrderStatus orderStatus) throws Exception {

                when(orderService.getCurrentCustomerOrderDetail(7L))
                                .thenReturn(createOrderDetail(orderStatus));

                mockMvc.perform(MockMvcRequestBuilders.get("/orders/7")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("order-detail"))
                                .andExpect(content().string(
                                                not(containsString("Cancel Order"))))
                                .andExpect(content().string(
                                                not(containsString("action=\"/orders/7/cancel\""))));
        }

        @Test
        void cancelOrder_missingCsrfToken_returnsForbidden() throws Exception {

                mockMvc.perform(MockMvcRequestBuilders.post("/orders/7/cancel")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
        }

        @Test
        void cancelOrder_adminUser_returnsForbidden() throws Exception {

                mockMvc.perform(MockMvcRequestBuilders.post("/orders/7/cancel")
                                .with(user("admin@example.com").roles("ADMIN"))
                                .with(csrf()))
                                .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
        }

        @Test
        void cancelOrder_unauthenticated_redirectsToLogin() throws Exception {

                mockMvc.perform(MockMvcRequestBuilders.post("/orders/7/cancel")
                                .with(csrf()))
                                .andExpect(status().is3xxRedirection());

                verifyNoInteractions(orderService);
        }

        @Test
        void cancelOrder_foreignOrMissingOrder_returnsNotFound() throws Exception {

                doThrow(new ResourceNotFoundException("Order not found"))
                                .when(orderService).cancelOrder(999L);

                mockMvc.perform(MockMvcRequestBuilders.post("/orders/999/cancel")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .with(csrf())
                                .accept("text/html"))
                                .andExpect(status().isNotFound())
                                .andExpect(view().name("error/404"));

                verify(orderService).cancelOrder(999L);
        }
}
