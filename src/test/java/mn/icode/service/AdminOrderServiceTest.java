package mn.icode.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.User;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.OrderStatus;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {
    @Mock OrderRepository repository;
    @Mock ProductRepository productRepository;

    @Test
    void filtersNormalizeEmailUseInclusiveUtcDatesAndStablePagination() {
        User user = new User();
        user.setEmail("nora@example.com");
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(12L);
        when(order.getUser()).thenReturn(user);
        when(order.getCreatedAt()).thenReturn(Instant.parse("2026-11-27T23:59:59Z"));
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getTotal()).thenReturn(new BigDecimal("20.00"));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        var result = new AdminOrderService(repository, productRepository).list(OrderStatus.PENDING,
                " Nora@Example.com ", LocalDate.parse("2026-11-26"),
                LocalDate.parse("2026-11-27"), 2, 5);

        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), paging.capture());
        assertEquals(2, paging.getValue().getPageNumber());
        assertEquals(5, paging.getValue().getPageSize());
        assertEquals("createdAt: DESC,id: DESC", paging.getValue().getSort().toString());
        assertEquals("nora@example.com", result.getContent().getFirst().customerEmail());
    }

    @Test
    void invalidDateRangeAndPaginationDoNotQueryDatabase() {
        AdminOrderService service = new AdminOrderService(repository, productRepository);
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, null, null, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, null, null, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null,
                LocalDate.parse("2026-11-28"), LocalDate.parse("2026-11-27"), 0, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void detailUsesStoredSnapshotsAndCustomerIdentity() {
        User user = new User();
        user.setEmail("nora@example.com");
        user.setFirstName("Nora");
        user.setLastName("Test");
        OrderItem item = mock(OrderItem.class);
        when(item.getProductName()).thenReturn("Original name");
        when(item.getUnitPrice()).thenReturn(new BigDecimal("12.00"));
        when(item.getQuantity()).thenReturn(2);
        when(item.getLineTotal()).thenReturn(new BigDecimal("24.00"));
        Order order = mock(Order.class);
        when(order.getUser()).thenReturn(user);
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(order.getShipCity()).thenReturn("Ulaanbaatar");
        when(order.getSubtotal()).thenReturn(new BigDecimal("24.00"));
        when(order.getTotal()).thenReturn(new BigDecimal("24.00"));
        when(order.getItems()).thenReturn(List.of(item));
        when(repository.findById(7L)).thenReturn(Optional.of(order));

        var detail = new AdminOrderService(repository, productRepository).detail(7L);
        assertEquals("Nora Test", detail.customerName());
        assertEquals("nora@example.com", detail.customerEmail());
        assertEquals("Ulaanbaatar", detail.shipCity());
        assertEquals("CONFIRMED", detail.status());
        assertEquals("Original name", detail.items().getFirst().getProductName());
        assertEquals(new BigDecimal("24.00"), detail.total());
    }

    @Test
    void missingDetailReturns404Exception() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> new AdminOrderService(repository, productRepository).detail(99L));
    }
}
