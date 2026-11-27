package mn.icode.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.PageController;
import mn.icode.dto.OrderDetailResponse;
import mn.icode.dto.OrderItemSummary;
import mn.icode.dto.OrderResponse;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;
import mn.icode.service.CartService;
import mn.icode.service.OrderService;
import mn.icode.service.UserService;

@WebMvcTest(controllers = PageController.class)
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class})
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
                .andExpect(content().string(containsString("No orders yet")));
    }

    @Test
    void orderHistory_withOrders_rendersOrderList() throws Exception {
        OrderItemSummary item = new OrderItemSummary(
                10L, "Snapshot Java Book", new BigDecimal("30.00"), 1, new BigDecimal("30.00"));
        OrderResponse order = new OrderResponse(
                42L, Instant.parse("2026-11-27T10:00:00Z"), "PENDING", new BigDecimal("30.00"), 1, List.of(item));

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
}