package mn.icode.dto;

import jakarta.validation.constraints.NotNull;
import mn.icode.model.OrderStatus;

/**
 * Request body for {@code PATCH /api/admin/orders/{id}/status}.
 *
 * <p>{@code expectedStatus} is the status the admin saw when the page was
 * loaded. It enables stale-update detection: if another admin already changed
 * the order, the request is rejected with {@code 409 Conflict} instead of
 * silently overwriting the newer state.</p>
 */
public record AdminOrderStatusUpdateRequest(
        @NotNull(message = "Target status is required") OrderStatus status,
        @NotNull(message = "Expected current status is required") OrderStatus expectedStatus) {
}
