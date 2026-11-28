package mn.icode.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explicit allowlist of admin-driven order status transitions.
 *
 * <p>The lifecycle intentionally mirrors the statuses already used by the
 * project ({@link OrderStatus}) and the customer cancellation rules from
 * CAP-27: an order may only move forward through the fulfilment flow or be
 * cancelled while it is still pending/confirmed.</p>
 */
public final class OrderStatusTransitionPolicy {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    private OrderStatusTransitionPolicy() {
    }

    /**
     * Returns the valid next statuses for the supplied status, in enum order.
     * Terminal statuses return an empty list.
     */
    public static List<OrderStatus> allowedNextStatuses(OrderStatus current) {
        if (current == null) {
            return List.of();
        }
        return ALLOWED.getOrDefault(current, Set.of())
                .stream()
                .sorted()
                .toList();
    }

    /**
     * Returns whether the supplied transition is explicitly allowlisted.
     * Same-status, backward, skipped and terminal-state transitions are rejected.
     */
    public static boolean isAllowed(OrderStatus current, OrderStatus target) {
        if (current == null || target == null) {
            return false;
        }
        return ALLOWED.getOrDefault(current, Set.of()).contains(target);
    }

    /**
     * A status is terminal when the policy allows no transition out of it.
     */
    public static boolean isTerminal(OrderStatus status) {
        return status != null && allowedNextStatuses(status).isEmpty();
    }
}
