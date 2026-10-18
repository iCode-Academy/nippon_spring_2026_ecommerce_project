package mn.icode.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import mn.icode.dto.RegistrationRequest;
import mn.icode.entity.Order;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;
import mn.icode.service.UserService;

@Controller
public class PageController {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public PageController(ProductRepository productRepository,
                          OrderRepository orderRepository,
                          UserRepository userRepository,
                          UserService userService) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("registration")) {
            model.addAttribute("registration", new RegistrationRequest());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registration") RegistrationRequest form,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        User user = new User();
        user.setFirstName(form.getFirstName());
        user.setLastName(form.getLastName());
        user.setEmail(form.getEmail());
        user.setPassword(form.getPassword());
        user.setPhone(form.getPhone());

        try {
            userService.registerUser(user);
        } catch (ConflictException e) {
            bindingResult.reject("email.exists", e.getMessage());
            return "register";
        } catch (RuntimeException e) {
            model.addAttribute("flashError",
                    "Registration could not be completed. Please try again.");
            return "register";
        }

        redirectAttributes.addFlashAttribute("flashSuccess",
                "Your account has been created. Please sign in.");
        return "redirect:/login";
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("products", productRepository.findByActiveTrue());
        return "home";
    }

    @GetMapping("/orders")
    public String orders(Model model, Authentication authentication) {
        List<Order> orders = Collections.emptyList();
        if (authentication != null) {
            orders = userRepository.findByEmail(authentication.getName())
                    .map(user -> orderRepository
                            .findByUserIdOrderByCreatedAtDesc(user.getId()))
                    .orElse(Collections.emptyList());
        }
        model.addAttribute("orders", orders);
        return "orders";
    }
}
