package mn.icode.controller;

import mn.icode.dto.OrderDetailResponse;
import mn.icode.dto.OrderResponse;
import mn.icode.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/orders")
public class OrderPageController {

    private final OrderService orderService;

    public OrderPageController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * GET /orders - Customer order history page with pagination
     * and empty-state handling when the customer has no orders.
     */
    @GetMapping
    public String orderHistory(Model model, Pageable pageable) {
        Page<OrderResponse> orderPage = orderService.getCurrentCustomerOrders(pageable.getPageNumber(), pageable.getPageSize());
        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("page", orderPage);
        return "orders";
    }

    /**
     * GET /orders/{id} - Customer order detail page displaying
     * order metadata, delivery snapshot/details, and all immutable
     * line-item snapshots.
     * Foreign or non-existent order IDs return 404 Not Found.
     */
    @GetMapping("/{id}")
    public String orderDetail(@PathVariable Long id, Model model) {
        try {
            OrderDetailResponse detail = orderService.getCurrentCustomerOrderDetail(id);
            model.addAttribute("detail", detail);
            return "order-detail";
        } catch (mn.icode.exception.ResourceNotFoundException e) {
            return "error/404";
        }
    }
}