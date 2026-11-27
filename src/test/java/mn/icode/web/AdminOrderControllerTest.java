package mn.icode.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

import mn.icode.config.SecurityConfig;
import mn.icode.controller.AdminOrderApiController;
import mn.icode.controller.AdminOrderPageController;
import mn.icode.dto.AdminOrderDetail;
import mn.icode.dto.AdminOrderSummary;
import mn.icode.dto.OrderItemSummary;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.OrderStatus;
import mn.icode.service.AdminOrderService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AdminOrderApiController.class, AdminOrderPageController.class})
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class})
class AdminOrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminOrderService service;

    @Test
    void customerCannotAccessEitherListOrDetail() throws Exception {
        for (String path : List.of("/admin/orders", "/admin/orders/7",
                "/api/admin/orders", "/api/admin/orders/7")) {
            mvc.perform(get(path).with(user("nora@example.com").roles("CUSTOMER")))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void anonymousRedirectsToLogin() throws Exception {
        mvc.perform(get("/admin/orders")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/admin/orders")).andExpect(status().is3xxRedirection());
    }

    @Test
    void apiReturnsPagedFilteredResultsAndEmptyResults() throws Exception {
        var summary = new AdminOrderSummary(7L, Instant.parse("2026-11-27T12:00:00Z"),
                "PENDING", "nora@example.com", new BigDecimal("24.00"));
        when(service.list(OrderStatus.PENDING, "nora@example.com", LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-27"), 1, 5))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(1, 5), 11));
        mvc.perform(get("/api/admin/orders").with(user("admin@example.com").roles("ADMIN"))
                        .param("status", "PENDING").param("email", "nora@example.com")
                        .param("from", "2026-11-01").param("to", "2026-11-27")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerEmail").value("nora@example.com"))
                .andExpect(jsonPath("$.totalElements").value(11));
        when(service.list(null, null, null, null, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/admin/orders").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void uiKeepsFiltersOnPaginationAndShowsEmptyState() throws Exception {
        var summary = new AdminOrderSummary(7L, Instant.parse("2026-11-27T12:00:00Z"),
                "PENDING", "nora@example.com", new BigDecimal("24.00"));
        when(service.list(OrderStatus.PENDING, "nora@example.com", LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-27"), 1, 5))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(1, 5), 11));
        mvc.perform(get("/admin/orders").with(user("admin@example.com").roles("ADMIN"))
                        .param("status", "PENDING").param("email", "nora@example.com")
                        .param("from", "2026-11-01").param("to", "2026-11-27")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andExpect(view().name("admin/orders"))
                .andExpect(content().string(containsString("page=2")))
                .andExpect(content().string(containsString("email=nora@example.com")))
                .andExpect(content().string(containsString("status=PENDING")))
                .andExpect(content().string(containsString("from=2026-11-01")))
                .andExpect(content().string(containsString("to=2026-11-27")))
                .andExpect(content().string(containsString("size=5")));
        when(service.list(null, null, null, null, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/admin/orders").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No orders found")));
    }

    @Test
    void detailRendersSnapshotsAndMissingIdReturns404() throws Exception {
        var detail = new AdminOrderDetail(7L, Instant.parse("2026-11-27T12:00:00Z"),
                "SHIPPED", "nora@example.com", "Nora Test", "Home", "Ulaanbaatar",
                "Sukhbaatar", "Peace Ave 1", "99112233", new BigDecimal("24.00"),
                new BigDecimal("24.00"), List.of(new OrderItemSummary(10L, "Original title",
                        new BigDecimal("12.00"), 2, new BigDecimal("24.00"))));
        when(service.detail(7L)).thenReturn(detail);
        mvc.perform(get("/api/admin/orders/7").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].productName").value("Original title"))
                .andExpect(jsonPath("$.customerEmail").value("nora@example.com"));
        mvc.perform(get("/admin/orders/7").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Original title")))
                .andExpect(content().string(containsString("Peace Ave 1")));
        when(service.detail(999L)).thenThrow(new ResourceNotFoundException("Order not found"));
        mvc.perform(get("/api/admin/orders/999").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/orders/999").accept("text/html")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(view().name("error/404"));
    }

    @Test
    void invalidRangeReportsUsefulError() throws Exception {
        when(service.list(null, null, LocalDate.parse("2026-11-28"),
                LocalDate.parse("2026-11-27"), 0, 20))
                .thenThrow(new IllegalArgumentException("from must be on or before to"));
        mvc.perform(get("/api/admin/orders").with(user("admin@example.com").roles("ADMIN"))
                        .param("from", "2026-11-28").param("to", "2026-11-27"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/orders").with(user("admin@example.com").roles("ADMIN"))
                        .param("from", "2026-11-28").param("to", "2026-11-27"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("from must be on or before to")));
    }
}
