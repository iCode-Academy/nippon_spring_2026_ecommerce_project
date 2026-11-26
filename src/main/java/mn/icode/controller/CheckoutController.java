package mn.icode.controller;

import mn.icode.dto.CheckoutRequest;
import mn.icode.dto.CheckoutResponse;
import mn.icode.service.CheckoutValidationService;
import mn.icode.service.AddressService;
import mn.icode.service.CartService;
import mn.icode.dto.CartResponse;
import mn.icode.entity.User;
import mn.icode.model.Role;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public CheckoutController(CheckoutValidationService validationService,
                              CartService cartService,
                              AddressService addressService) {
        this.validationService = validationService;
        this.cartService = cartService;
        this.addressService = addressService;
    }

    /** Show checkout page with cart and user's addresses. */
    @GetMapping
    public String showCheckoutForm(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        // Get the current user's email from principal
        String email = principal.getName();

        // Get cart response
        CartResponse cartResponse = cartService.getCart(email);

        // Add user's owned addresses for selection
        List<mn.icode.dto.AddressResponse> addresses = addressService.listAddresses();
        model.addAttribute("addresses", addresses);

        // Add cart and checkout request to model
        model.addAttribute("cart", cartResponse);
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
            CartResponse cartResponse = cartService.getCart(email);
            model.addAttribute("cart", cartResponse);
            model.addAttribute("checkoutRequest", request);
            return "checkout";
        }

        // Perform pre-persistence validation
        CheckoutResponse validationResult = validationService.validateCheckout(request.getDeliveryAddressId());

        if (!validationResult.isSuccess()) {
            // Validation failed - return to checkout page with error messages
            CartResponse cartResponse = cartService.getCart(email);
            model.addAttribute("cart", cartResponse);
            model.addAttribute("checkoutRequest", request);
            model.addAttribute("validationErrors", validationResult.getErrors());
            return "checkout";
        }

        // Validation passed - proceed with order creation
        // TODO: Create order persistence (out of scope for CAP-23 validation focus)
        // For now, just redirect to order confirmation or cart page

        return "redirect:/orders/confirmation";
    }
}