package mn.icode.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mn.icode.dto.OrderDetailResponse;
import mn.icode.dto.OrderResponse;
import mn.icode.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderApiController {

    private final OrderService orderService;

    public OrderApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * GET /api/orders - Return a paginated list of the currently authenticated
     * customer's orders with deterministic ordering (createdAt DESC, id DESC).
     */
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> listMyOrders(Pageable pageable) {
        Page<OrderResponse> orders = orderService.getCurrentCustomerOrders(pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(orders);
    }

    /**
     * GET /api/orders/{id} - Return full order details including immutable
     * order item snapshots for the authenticated customer's order.
     * Guessed or foreign order IDs return 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> getMyOrder(@PathVariable Long id) {
        OrderDetailResponse detail = orderService.getCurrentCustomerOrderDetail(id);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }
}