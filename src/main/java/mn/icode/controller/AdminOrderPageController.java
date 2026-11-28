package mn.icode.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import mn.icode.model.OrderStatus;
import mn.icode.service.AdminOrderService;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderPageController {
    private final AdminOrderService orders;

    public AdminOrderPageController(AdminOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public String list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Model model) {
        try {
            model.addAttribute("page", orders.list(status, email, from, to, page, size));
        } catch (IllegalArgumentException e) {
            model.addAttribute("page", new PageImpl<>(java.util.List.of()));
            model.addAttribute("filterError", e.getMessage());
        }
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("email", email);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "admin/orders";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("order", orders.detail(id));
        return "admin/order-detail";
    }
}
