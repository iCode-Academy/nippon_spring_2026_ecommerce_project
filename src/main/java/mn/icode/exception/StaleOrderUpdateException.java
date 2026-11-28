package mn.icode.exception;

import mn.icode.model.OrderStatus;

/**
 * Raised when the status supplied by the client no longer matches the current
 * persisted status, which means another admin changed the order first.
 */
public class StaleOrderUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final OrderStatus expectedStatus;
    private final OrderStatus currentStatus;

    public StaleOrderUpdateException(OrderStatus expectedStatus, OrderStatus currentStatus) {
        super("Order status changed from " + expectedStatus + " to " + currentStatus
                + " after this page was loaded. Refresh the order and try again.");
        this.expectedStatus = expectedStatus;
        this.currentStatus = currentStatus;
    }

    public OrderStatus getExpectedStatus() {
        return expectedStatus;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }
}
