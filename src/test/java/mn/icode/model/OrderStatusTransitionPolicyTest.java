package mn.icode.model;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTransitionPolicyTest {

    @Test
    void allowlistContainsEveryLegalForwardAndCancellationTransition() {
        assertTrue(OrderStatusTransitionPolicy.isAllowed(OrderStatus.PENDING, OrderStatus.CONFIRMED));
        assertTrue(OrderStatusTransitionPolicy.isAllowed(OrderStatus.PENDING, OrderStatus.CANCELLED));
        assertTrue(OrderStatusTransitionPolicy.isAllowed(OrderStatus.CONFIRMED, OrderStatus.SHIPPED));
        assertTrue(OrderStatusTransitionPolicy.isAllowed(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        assertTrue(OrderStatusTransitionPolicy.isAllowed(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    @Test
    void allowedNextStatusesAreExplicitAndOrdered() {
        assertEquals(List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
                OrderStatusTransitionPolicy.allowedNextStatuses(OrderStatus.PENDING));
        assertEquals(List.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
                OrderStatusTransitionPolicy.allowedNextStatuses(OrderStatus.CONFIRMED));
        assertEquals(List.of(OrderStatus.DELIVERED),
                OrderStatusTransitionPolicy.allowedNextStatuses(OrderStatus.SHIPPED));
        assertEquals(List.of(), OrderStatusTransitionPolicy.allowedNextStatuses(OrderStatus.DELIVERED));
        assertEquals(List.of(), OrderStatusTransitionPolicy.allowedNextStatuses(OrderStatus.CANCELLED));
    }

    @Test
    void arbitraryTransitionsThatSkipStatesAreRejected() {
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.PENDING, OrderStatus.SHIPPED));
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.PENDING, OrderStatus.DELIVERED));
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.CONFIRMED, OrderStatus.DELIVERED));
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void repeatedSameStatusTransitionsAreRejected(OrderStatus status) {
        assertFalse(OrderStatusTransitionPolicy.isAllowed(status, status));
    }

    @Test
    void backwardTransitionsAreRejected() {
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.CONFIRMED, OrderStatus.PENDING));
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.SHIPPED, OrderStatus.CONFIRMED));
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.SHIPPED, OrderStatus.PENDING));
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.DELIVERED, OrderStatus.SHIPPED));
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.CANCELLED, OrderStatus.PENDING));
    }

    @Test
    void terminalStatesAllowNoTransitions() {
        assertTrue(OrderStatusTransitionPolicy.isTerminal(OrderStatus.DELIVERED));
        assertTrue(OrderStatusTransitionPolicy.isTerminal(OrderStatus.CANCELLED));
        assertFalse(OrderStatusTransitionPolicy.isTerminal(OrderStatus.PENDING));
        assertFalse(OrderStatusTransitionPolicy.isTerminal(OrderStatus.CONFIRMED));
        assertFalse(OrderStatusTransitionPolicy.isTerminal(OrderStatus.SHIPPED));

        for (OrderStatus target : OrderStatus.values()) {
            assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.DELIVERED, target));
            assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.CANCELLED, target));
        }
    }

    @Test
    void nullStatusesAreRejectedSafely() {
        assertFalse(OrderStatusTransitionPolicy.isAllowed(null, OrderStatus.CONFIRMED));
        assertFalse(OrderStatusTransitionPolicy.isAllowed(OrderStatus.PENDING, null));
        assertFalse(OrderStatusTransitionPolicy.isTerminal(null));
        assertEquals(List.of(), OrderStatusTransitionPolicy.allowedNextStatuses(null));
    }
}
