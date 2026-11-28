package mn.icode.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.AdminOrderApiController;
import mn.icode.controller.AdminOrderPageController;
import mn.icode.dto.AdminOrderDetail;
import mn.icode.dto.OrderItemSummary;
import mn.icode.exception.InvalidOrderTransitionException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.exception.StaleOrderUpdateException;
import mn.icode.model.OrderStatus;
import mn.icode.model.OrderStatusTransitionPolicy;
import mn.icode.service.AdminOrderService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {AdminOrderApiController.class, AdminOrderPageController.class})
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class})
class AdminOrderStatusControllerTest {

    private static final String STATUS_URL = "/api/admin/orders/7/status";

    @Autowired MockMvc mvc;
    @MockitoBean AdminOrderService service;

    @Test
    void adminCanApplyAllowlistedTransition() throws Exception {
        when(service.updateStatus(7L, OrderStatus.CONFIRMED, OrderStatus.PENDING))
                .thenReturn(detail(7L, "CONFIRMED"));

        mvc.perform(patch(STATUS_URL)
                        .with(csrf())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void customerIsDeniedTheStatusEndpoint() throws Exception {
        mvc.perform(patch(STATUS_URL)
                        .with(csrf())
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestFollowsExistingLoginRedirect() throws Exception {
        mvc.perform(patch(STATUS_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void missingCsrfTokenIsRejected() throws Exception {
        mvc.perform(patch(STATUS_URL)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void invalidCsrfTokenIsRejected() throws Exception {
        mvc.perform(patch(STATUS_URL)
                        .with(csrf().useInvalidToken())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void invalidTransitionReturnsStructuredConflict() throws Exception {
        when(service.updateStatus(7L, OrderStatus.SHIPPED, OrderStatus.PENDING))
                .thenThrow(new InvalidOrderTransitionException(OrderStatus.PENDING,
                        OrderStatus.SHIPPED,
                        OrderStatusTransitionPolicy.allowedNextStatuses(OrderStatus.PENDING)));

        mvc.perform(patch(STATUS_URL)
                        .with(csrf())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHIPPED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid order transition"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.toStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.detail").value(containsString("Allowed next statuses")));
    }

    @Test
    void staleUpdateReturnsStructuredConflict() throws Exception {
        when(service.updateStatus(7L, OrderStatus.SHIPPED, OrderStatus.PENDING))
                .thenThrow(new StaleOrderUpdateException(OrderStatus.PENDING, OrderStatus.CONFIRMED));

        mvc.perform(patch(STATUS_URL)
                        .with(csrf())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHIPPED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Stale order update"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("STALE_UPDATE"))
                .andExpect(jsonPath("$.expectedStatus").value("PENDING"))
                .andExpect(jsonPath("$.currentStatus").value("CONFIRMED"));
    }

    @Test
    void missingOrderReturnsNotFound() throws Exception {
        when(service.updateStatus(7L, OrderStatus.CONFIRMED, OrderStatus.PENDING))
                .thenThrow(new ResourceNotFoundException("Order not found"));

        mvc.perform(patch(STATUS_URL)
                        .with(csrf())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void missingRequiredFieldsReturnValidationErrors() throws Exception {
        mvc.perform(patch(STATUS_URL)
                        .with(csrf())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":null,"expectedStatus":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.status").value("Target status is required"))
                .andExpect(jsonPath("$.errors.expectedStatus")
                        .value("Expected current status is required"));

        verifyNoInteractions(service);
    }

    @Test
    void unknownStatusValueReturnsBadRequest() throws Exception {
        mvc.perform(patch(STATUS_URL)
                        .with(csrf())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"TELEPORTED","expectedStatus":"PENDING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        verifyNoInteractions(service);
    }

    @Test
    void detailUiOnlyRendersAllowlistedNextActions() throws Exception {
        when(service.detail(7L)).thenReturn(detail(7L, "PENDING"));

        mvc.perform(get("/admin/orders/7").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/order-detail"))
                .andExpect(content().string(containsString("id=\"orderStatusPanel\"")))
                .andExpect(content().string(containsString("data-status=\"CONFIRMED\"")))
                .andExpect(content().string(containsString("data-status=\"CANCELLED\"")))
                .andExpect(content().string(not(containsString("data-status=\"SHIPPED\""))))
                .andExpect(content().string(not(containsString("data-status=\"DELIVERED\""))))
                .andExpect(content().string(containsString("js/admin-order-status.js")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void terminalDetailUiExplainsThatNoActionsAreAvailable() throws Exception {
        when(service.detail(8L)).thenReturn(detail(8L, "DELIVERED"));

        mvc.perform(get("/admin/orders/8").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("terminal state")))
                .andExpect(content().string(not(containsString("data-status="))));
    }

    private AdminOrderDetail detail(Long id, String status) {
        return new AdminOrderDetail(id, Instant.parse("2026-11-27T12:00:00Z"), status,
                "nora@example.com", "Nora Test", "Home", "Ulaanbaatar", "Sukhbaatar",
                "Peace Ave 1", "99112233", new BigDecimal("24.00"),
                new BigDecimal("24.00"), List.of(new OrderItemSummary(10L, "Original title",
                        new BigDecimal("12.00"), 2, new BigDecimal("24.00"))));
    }
}
