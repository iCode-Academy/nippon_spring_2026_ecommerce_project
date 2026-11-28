package mn.icode.exception;

import java.util.List;
import java.util.stream.Collectors;

import mn.icode.model.OrderStatus;

/**
 * Raised when an admin requests a transition that is not on the allowlisted
 * order status lifecycle (including same-status, backward, skipped and
 * terminal-state transitions).
 */
public class InvalidOrderTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final OrderStatus fromStatus;
    private final OrderStatus toStatus;

    public InvalidOrderTransitionException(OrderStatus fromStatus, OrderStatus toStatus,
            List<OrderStatus> allowedNextStatuses) {
        super(buildMessage(fromStatus, toStatus, allowedNextStatuses));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    private static String buildMessage(OrderStatus fromStatus, OrderStatus toStatus,
            List<OrderStatus> allowedNextStatuses) {
        if (fromStatus == toStatus) {
            return "Order is already in status " + fromStatus
                    + ". Choose a different status.";
        }
        if (allowedNextStatuses == null || allowedNextStatuses.isEmpty()) {
            return "Order status " + fromStatus
                    + " is terminal and cannot be changed to " + toStatus + ".";
        }
        String allowed = allowedNextStatuses.stream()
                .map(OrderStatus::name)
                .collect(Collectors.joining(", "));
        return "Cannot change order status from " + fromStatus + " to " + toStatus
                + ". Allowed next statuses: " + allowed + ".";
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }
}
