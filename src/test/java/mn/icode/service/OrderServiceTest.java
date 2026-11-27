package mn.icode.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import mn.icode.dto.OrderDetailResponse;
import mn.icode.dto.OrderResponse;
import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.User;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.OrderStatus;
import mn.icode.model.Role;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    private User customer;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, userRepository);

        customer = new User();
        customer.setId(1L);
        customer.setEmail("nora@example.com");
        customer.setRole(Role.CUSTOMER);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nora@example.com", "secret", List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Order mockOrder(Long orderId, Instant createdAt, String snapshotProductName, BigDecimal snapshotUnitPrice, int qty) {
        OrderItem item = mock(OrderItem.class);
        lenient().when(item.getProductId()).thenReturn(10L);
        lenient().when(item.getProductName()).thenReturn(snapshotProductName);
        lenient().when(item.getUnitPrice()).thenReturn(snapshotUnitPrice);
        lenient().when(item.getQuantity()).thenReturn(qty);
        lenient().when(item.getLineTotal()).thenReturn(snapshotUnitPrice.multiply(BigDecimal.valueOf(qty)));

        Order order = mock(Order.class);
        lenient().when(order.getId()).thenReturn(orderId);
        lenient().when(order.getCreatedAt()).thenReturn(createdAt);
        lenient().when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        lenient().when(order.getShipTitle()).thenReturn("Home");
        lenient().when(order.getShipCity()).thenReturn("Ulaanbaatar");
        lenient().when(order.getShipDistrict()).thenReturn("Sukhbaatar");
        lenient().when(order.getShipAddressLine()).thenReturn("Peace Ave 1");
        lenient().when(order.getShipPhone()).thenReturn("99112233");
        lenient().when(order.getSubtotal()).thenReturn(snapshotUnitPrice.multiply(BigDecimal.valueOf(qty)));
        lenient().when(order.getTotal()).thenReturn(snapshotUnitPrice.multiply(BigDecimal.valueOf(qty)));
        lenient().when(order.getItems()).thenReturn(List.of(item));
        return order;
    }

    @Test
    void getCurrentCustomerOrders_returnsPaginatedOwnedOrdersInDeterministicOrder() {
        Order newerOrder = mockOrder(20L, Instant.parse("2026-11-27T10:00:00Z"), "Spring Boot Guide", new BigDecimal("30.00"), 1);
        Order olderOrder = mockOrder(10L, Instant.parse("2026-11-26T10:00:00Z"), "Java 25 Book", new BigDecimal("25.00"), 2);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> repoPage = new PageImpl<>(List.of(newerOrder, olderOrder), pageable, 2);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class))).thenReturn(repoPage);

        Page<OrderResponse> result = orderService.getCurrentCustomerOrders(0, 10);

        assertEquals(2, result.getTotalElements());
        assertEquals(20L, result.getContent().get(0).getId());
        assertEquals(10L, result.getContent().get(1).getId());
        verify(orderRepository).findByUserIdOrderByCreatedAtDescIdDesc(1L, PageRequest.of(0, 10));
    }

    @Test
    void getCurrentCustomerOrders_emptyHistory_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<OrderResponse> result = orderService.getCurrentCustomerOrders(0, 10);

        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());
    }

    @Test
    void getCurrentCustomerOrderDetail_ownedOrder_returnsImmutableSnapshots() {
        Order ownedOrder = mockOrder(5L, Instant.parse("2026-11-27T12:00:00Z"), "Original Snapshot Title", new BigDecimal("19.99"), 2);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(ownedOrder));

        OrderDetailResponse detail = orderService.getCurrentCustomerOrderDetail(5L);

        assertEquals(5L, detail.getId());
        assertEquals(1, detail.getItems().size());
        assertEquals("Original Snapshot Title", detail.getItems().get(0).getProductName());
        assertEquals(new BigDecimal("19.99"), detail.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("39.98"), detail.getItems().get(0).getLineTotal());
    }

    @Test
    void getCurrentCustomerOrderDetail_foreignOrMissingOrder_throwsNotFound() {
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getCurrentCustomerOrderDetail(999L));
    }
}