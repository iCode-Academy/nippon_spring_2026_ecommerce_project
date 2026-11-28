package mn.icode.controller;

import mn.icode.dto.CheckoutRequest;
import mn.icode.dto.CheckoutResponse;
import mn.icode.service.CheckoutValidationService;
import mn.icode.service.AddressService;
import mn.icode.service.CartService;
import mn.icode.service.CheckoutService;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.exception.ConflictException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutValidationService validationService;
    private final CartService cartService;
    private final AddressService addressService;
    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutValidationService validationService,
                               CartService cartService,
                               AddressService addressService,
                               CheckoutService checkoutService) {
        this.validationService = validationService;
        this.cartService = cartService;
        this.addressService = addressService;
        this.checkoutService = checkoutService;
    }

    /** Show checkout page with cart and user's addresses. */
    @GetMapping
    public String showCheckoutForm(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        populateForm(model, principal.getName());
        model.addAttribute("checkoutRequest", new CheckoutRequest());

        return "checkout";
    }

    /** Process checkout submission with validation. */
    @PostMapping
    public String processCheckout(@Valid @ModelAttribute("checkoutRequest") CheckoutRequest request,
                                  BindingResult result,
                                  Model model,
                                  Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        String email = principal.getName();

        // Check for binding errors first
        if (result.hasErrors()) {
            populateForm(model, email);
            return "checkout";
        }

        // Perform pre-persistence validation
        CheckoutResponse validationResult = validationService.validateCheckout(request.getDeliveryAddressId());

        if (!validationResult.isSuccess()) {
            populateForm(model, email);
            model.addAttribute("validationErrors", validationResult.getErrors());
            return "checkout";
        }

        // Preflight is advisory; checkout rechecks ownership, stock and totals under locks.
        try {
            var order = checkoutService.checkout(email, request.getDeliveryAddressId());
            return "redirect:/orders/" + order.getId();
        } catch (ConflictException | ResourceNotFoundException | IllegalArgumentException e) {
            populateForm(model, email);
            model.addAttribute("validationErrors", List.of(e.getMessage()));
            return "checkout";
        }
    }

    private void populateForm(Model model, String email) {
        model.addAttribute("cart", cartService.getCart(email));
        model.addAttribute("addresses", addressService.listAddresses());
    }
}
