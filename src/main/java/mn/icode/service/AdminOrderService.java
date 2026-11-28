package mn.icode.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.dto.AdminOrderDetail;
import mn.icode.dto.AdminOrderSummary;
import mn.icode.dto.OrderItemSummary;
import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.Product;
import mn.icode.exception.ConflictException;
import mn.icode.exception.InvalidOrderTransitionException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.exception.StaleOrderUpdateException;
import mn.icode.model.OrderStatus;
import mn.icode.model.OrderStatusTransitionPolicy;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;

@Service
public class AdminOrderService {
    private final OrderRepository orders;
    private final ProductRepository products;

    public AdminOrderService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderSummary> list(OrderStatus status, String email, LocalDate from,
                                        LocalDate to, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be on or before to");
        }
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        Instant start = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant until = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Specification<Order> filter = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (normalizedEmail != null) predicates.add(cb.equal(root.get("user").get("email"), normalizedEmail));
            if (start != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            if (until != null) predicates.add(cb.lessThan(root.get("createdAt"), until));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return orders.findAll(filter,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(order -> new AdminOrderSummary(order.getId(), order.getCreatedAt(),
                        order.getStatus().name(), order.getUser().getEmail(), order.getTotal()));
    }

    @Transactional(readOnly = true)
    public AdminOrderDetail detail(Long id) {
        Order order = orders.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return toDetail(order);
    }

    /**
     * Applies an allowlisted admin status transition.
     *
     * <p>The order row is locked for the duration of the transaction. The
     * client's {@code expectedStatus} is compared with the locked row before
     * any change is made, so two admins editing the same order cannot silently
     * overwrite each other. Cancelling through the admin API reuses the same
     * stock-restoration rules as the CAP-27 customer cancellation flow.</p>
     */
    @Transactional
    public AdminOrderDetail updateStatus(Long id, OrderStatus targetStatus,
            OrderStatus expectedStatus) {
        Order order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus != expectedStatus) {
            throw new StaleOrderUpdateException(expectedStatus, currentStatus);
        }
        if (!OrderStatusTransitionPolicy.isAllowed(currentStatus, targetStatus)) {
            throw new InvalidOrderTransitionException(currentStatus, targetStatus,
                    OrderStatusTransitionPolicy.allowedNextStatuses(currentStatus));
        }

        if (targetStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
        }
        order.setStatus(targetStatus);
        return toDetail(order);
    }

    /**
     * Restores stock for an admin cancellation using the CAP-27 rules: lock
     * products in ascending product-id order, restore purchased quantities,
     * and fail the whole transaction if a product is missing or stock overflows.
     */
    private void restoreStock(Order order) {
        List<OrderItem> items = new ArrayList<>(order.getItems());
        items.sort(Comparator.comparing(OrderItem::getProductId));

        for (OrderItem item : items) {
            Product product = products.findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new ConflictException(
                            "Cannot restore stock: product not found"));
            try {
                product.increaseStock(item.getQuantity());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException(exception.getMessage());
            }
        }
    }

    private AdminOrderDetail toDetail(Order order) {
        var user = order.getUser();
        var items = order.getItems().stream().map(item -> new OrderItemSummary(
                item.getProductId(), item.getProductName(), item.getUnitPrice(),
                item.getQuantity(), item.getLineTotal())).toList();
        return new AdminOrderDetail(order.getId(), order.getCreatedAt(), order.getStatus().name(),
                user.getEmail(), user.getFirstName() + " " + user.getLastName(),
                order.getShipTitle(), order.getShipCity(), order.getShipDistrict(),
                order.getShipAddressLine(), order.getShipPhone(), order.getSubtotal(),
                order.getTotal(), items);
    }
}
