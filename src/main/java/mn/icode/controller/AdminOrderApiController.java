package mn.icode.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import mn.icode.dto.AdminOrderDetail;
import mn.icode.dto.AdminOrderStatusUpdateRequest;
import mn.icode.dto.AdminOrderSummary;
import mn.icode.model.OrderStatus;
import mn.icode.service.AdminOrderService;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderApiController {
    private final AdminOrderService orders;

    public AdminOrderApiController(AdminOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public Page<AdminOrderSummary> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return orders.list(status, email, from, to, page, size);
    }

    @GetMapping("/{id}")
    public AdminOrderDetail detail(@PathVariable Long id) {
        return orders.detail(id);
    }

    /**
     * PATCH /api/admin/orders/{id}/status
     *
     * <p>Requires the {@code ADMIN} role and a valid CSRF token. The request
     * must include both the target {@code status} and the {@code expectedStatus}
     * the admin saw, which protects against stale concurrent updates.</p>
     */
    @PatchMapping("/{id}/status")
    public AdminOrderDetail updateStatus(@PathVariable Long id,
            @Valid @RequestBody AdminOrderStatusUpdateRequest request) {
        return orders.updateStatus(id, request.status(), request.expectedStatus());
    }
}
