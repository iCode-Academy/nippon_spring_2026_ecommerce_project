package mn.icode.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.dto.OrderDetailResponse;
import mn.icode.dto.OrderItemSummary;
import mn.icode.dto.OrderResponse;
import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.OrderStatus;
import mn.icode.model.Role;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;

@Service
public class OrderService {

   private final OrderRepository orderRepository;
private final UserRepository userRepository;
private final ProductRepository productRepository;

public OrderService(OrderRepository orderRepository,
                    UserRepository userRepository,
                    ProductRepository productRepository) {
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
}

    /** Get the currently authenticated user's email. */
    private String getCurrentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** Get the currently authenticated user. */
    private User getCurrentUser() {
        String email = getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new mn.icode.exception.ResourceNotFoundException(
                        "User not found with email: " + email));
    }

    /** Get paginated orders for the current customer with deterministic ordering. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getCurrentCustomerOrders(int page, int size) {
        User user = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orderPage = orderRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                user.getId(), pageable);

        List<OrderResponse> content = orderPage.getContent().stream().map(order -> {
            List<OrderItemSummary> itemSummaries = order.getItems().stream()
                    .map(item -> new OrderItemSummary(
                            item.getProductId(),
                            item.getProductName(),
                            item.getUnitPrice(),
                            item.getQuantity(),
                            item.getLineTotal()))
                    .collect(Collectors.toList());
            return new OrderResponse(
                    order.getId(),
                    order.getCreatedAt(),
                    order.getStatus().name(),
                    order.getTotal(),
                    order.getItems().size(),
                    itemSummaries);
        }).collect(Collectors.toList());

        return new PageImpl<>(content, pageable, orderPage.getTotalElements());
    }

    /** Get full order detail for the current customer, or 404 if not found/owned. */
    @Transactional(readOnly = true)
    public OrderDetailResponse getCurrentCustomerOrderDetail(Long orderId) {
        User user = getCurrentUser();

        return orderRepository.findByIdAndUserId(orderId, user.getId())
                .map(order -> {
                    List<OrderItemSummary> itemSummaries = order.getItems().stream()
                            .map(item -> new OrderItemSummary(
                                    item.getProductId(),
                                    item.getProductName(),
                                    item.getUnitPrice(),
                                    item.getQuantity(),
                                    item.getLineTotal()))
                            .collect(Collectors.toList());

                    return new OrderDetailResponse(
                            order.getId(),
                            order.getCreatedAt(),
                            order.getStatus().name(),
                            order.getShipTitle(),
                            order.getShipCity(),
                            order.getShipDistrict(),
                            order.getShipAddressLine(),
                            order.getShipPhone(),
                            order.getSubtotal(),
                            order.getTotal(),
                            order.getItems().size(),
                            itemSummaries);
                })
                .orElseThrow(() -> new mn.icode.exception.ResourceNotFoundException(
                        "Order not found with id " + orderId + " or you do not have permission to view it"));
    }

    //Order cancellation
    @Transactional
    public void cancelOrder(Long orderId) {

        // validate user
        User customer = userRepository.findByEmailForUpdate(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Customer not found"));

        if(!customer.isEnabled() || customer.getRole() != Role.CUSTOMER) {
                throw new AccessDeniedException("Access denied");
        }

        //find and lock the customer order
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, customer.getId())
                .orElseThrow(() -> 
                new ResourceNotFoundException("Order not found"));

        //check cancellation eligibility
        if(!order.getStatus().isCancellable()) {
                throw new ConflictException("Order cannot be cancelled in its current status");
        
        }

        //sort order items by product id
        List<OrderItem> items = new ArrayList<>(order.getItems());
        items.sort(Comparator.comparing(OrderItem::getProductId));

        //Restore inventory
        for (OrderItem item: items) {
                Product product = productRepository.findByIdForUpdate(item.getProductId())
                        .orElseThrow(() -> new ConflictException("Cannot restore stock: product not found"));
                
                try {
                    product.increaseStock(item.getQuantity());
                } catch (IllegalArgumentException e) {
                        throw new ConflictException(e.getMessage());
                }
        }
        order.setStatus(OrderStatus.CANCELLED);
     }

    /** Convert an Order entity to OrderResponse (using only snapshot fields). */
    private OrderResponse fromEntity(Order order) {
        List<OrderItemSummary> itemSummaries = order.getItems().stream()
                .map(item -> new OrderItemSummary(
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineTotal()))
                .collect(Collectors.toList());
        return new OrderResponse(
                order.getId(),
                order.getCreatedAt(),
                order.getStatus().name(),
                order.getTotal(),
                order.getItems().size(),
                itemSummaries);
    }
}