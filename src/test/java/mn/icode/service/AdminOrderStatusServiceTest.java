package mn.icode.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.InvalidOrderTransitionException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.exception.StaleOrderUpdateException;
import mn.icode.model.OrderStatus;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderStatusServiceTest {

    @Mock OrderRepository orders;
    @Mock ProductRepository products;

    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orders, products);
    }

    @Test
    void validForwardTransitionPersistsTargetStatus() {
        Order order = orderWithStatus(OrderStatus.PENDING, OrderStatus.CONFIRMED);
        stubDetail(order, List.of());
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        var detail = service.updateStatus(7L, OrderStatus.CONFIRMED, OrderStatus.PENDING);

        assertEquals("CONFIRMED", detail.status());
        verify(order).setStatus(OrderStatus.CONFIRMED);
        verifyNoInteractions(products);
    }

    @Test
    void repeatedStatusTransitionIsRejected() {
        Order order = orderWithStatus(OrderStatus.PENDING);
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderTransitionException.class,
                () -> service.updateStatus(7L, OrderStatus.PENDING, OrderStatus.PENDING));
        verify(order, never()).setStatus(any());
    }

    @Test
    void arbitrarySkippedTransitionIsRejected() {
        Order order = orderWithStatus(OrderStatus.PENDING);
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderTransitionException.class,
                () -> service.updateStatus(7L, OrderStatus.SHIPPED, OrderStatus.PENDING));
        verify(order, never()).setStatus(any());
    }

    @Test
    void backwardTransitionIsRejected() {
        Order order = orderWithStatus(OrderStatus.SHIPPED);
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderTransitionException.class,
                () -> service.updateStatus(7L, OrderStatus.CONFIRMED, OrderStatus.SHIPPED));
        verify(order, never()).setStatus(any());
    }

    @Test
    void terminalStateTransitionIsRejected() {
        Order delivered = orderWithStatus(OrderStatus.DELIVERED);
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(delivered));

        assertThrows(InvalidOrderTransitionException.class,
                () -> service.updateStatus(7L, OrderStatus.SHIPPED, OrderStatus.DELIVERED));

        Order cancelled = orderWithStatus(OrderStatus.CANCELLED);
        when(orders.findByIdForUpdate(8L)).thenReturn(Optional.of(cancelled));

        assertThrows(InvalidOrderTransitionException.class,
                () -> service.updateStatus(8L, OrderStatus.PENDING, OrderStatus.CANCELLED));

        verify(delivered, never()).setStatus(any());
        verify(cancelled, never()).setStatus(any());
    }

    @Test
    void staleExpectedStatusIsRejectedBeforeAnyMutation() {
        Order order = orderWithStatus(OrderStatus.CONFIRMED);
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        assertThrows(StaleOrderUpdateException.class,
                () -> service.updateStatus(7L, OrderStatus.SHIPPED, OrderStatus.PENDING));

        verify(order, never()).setStatus(any());
        verifyNoInteractions(products);
    }

    @Test
    void missingOrderIsReportedAsNotFound() {
        when(orders.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateStatus(99L, OrderStatus.CONFIRMED, OrderStatus.PENDING));
    }

    @Test
    void adminCancellationRestoresStockWithCap27Rules() {
        OrderItem item = mock(OrderItem.class);
        when(item.getProductId()).thenReturn(10L);
        when(item.getQuantity()).thenReturn(2);

        Product product = mock(Product.class);
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        Order order = orderWithStatus(OrderStatus.PENDING, OrderStatus.CANCELLED);
        stubDetail(order, List.of(item));
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        var detail = service.updateStatus(7L, OrderStatus.CANCELLED, OrderStatus.PENDING);

        assertEquals("CANCELLED", detail.status());
        verify(product).increaseStock(2);
        verify(order).setStatus(OrderStatus.CANCELLED);
    }

    @Test
    void adminCancellationFailsWhenProductIsMissing() {
        OrderItem item = mock(OrderItem.class);
        when(item.getProductId()).thenReturn(10L);

        Order order = orderWithStatus(OrderStatus.CONFIRMED);
        when(order.getItems()).thenReturn(List.of(item));
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class,
                () -> service.updateStatus(7L, OrderStatus.CANCELLED, OrderStatus.CONFIRMED));
        verify(order, never()).setStatus(any());
    }

    @Test
    void adminCancellationFailsOnStockOverflow() {
        OrderItem item = mock(OrderItem.class);
        when(item.getProductId()).thenReturn(10L);
        when(item.getQuantity()).thenReturn(2);

        Product product = mock(Product.class);
        doThrow(new IllegalArgumentException("Stock quantity exceeds maximum allowed value"))
                .when(product).increaseStock(2);

        Order order = orderWithStatus(OrderStatus.PENDING);
        when(order.getItems()).thenReturn(List.of(item));
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        assertThrows(ConflictException.class,
                () -> service.updateStatus(7L, OrderStatus.CANCELLED, OrderStatus.PENDING));
        verify(order, never()).setStatus(any());
    }

    private Order orderWithStatus(OrderStatus... statuses) {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(statuses[0],
                java.util.Arrays.copyOfRange(statuses, 1, statuses.length));
        return order;
    }

    private void stubDetail(Order order, List<OrderItem> items) {
        User user = new User();
        user.setEmail("nora@example.com");
        user.setFirstName("Nora");
        user.setLastName("Test");

        when(order.getId()).thenReturn(7L);
        when(order.getCreatedAt()).thenReturn(Instant.parse("2026-11-27T12:00:00Z"));
        when(order.getUser()).thenReturn(user);
        when(order.getShipTitle()).thenReturn("Home");
        when(order.getShipCity()).thenReturn("Ulaanbaatar");
        when(order.getShipDistrict()).thenReturn("Sukhbaatar");
        when(order.getShipAddressLine()).thenReturn("Peace Ave 1");
        when(order.getShipPhone()).thenReturn("99112233");
        when(order.getSubtotal()).thenReturn(new BigDecimal("24.00"));
        when(order.getTotal()).thenReturn(new BigDecimal("24.00"));
        when(order.getItems()).thenReturn(items);
    }
}
