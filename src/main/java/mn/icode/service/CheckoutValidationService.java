package mn.icode.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.dto.CartResponse;
import mn.icode.dto.CheckoutRequest;
import mn.icode.dto.CheckoutResponse;
import mn.icode.entity.Address;
import mn.icode.entity.Cart;
import mn.icode.entity.CartItem;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.model.Role;
import mn.icode.repository.AddressRepository;
import mn.icode.repository.CartItemRepository;
import mn.icode.repository.CartRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;

/**
 * Validate checkout inputs before any persistence changes.
 */
@Service
public class CheckoutValidationService {

    private final AddressRepository addressRepository;
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CheckoutValidationService(AddressRepository addressRepository,
                                     CartItemRepository cartItemRepository,
                                     CartRepository cartRepository,
                                     ProductRepository productRepository,
                                     UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /** Validate checkout inputs before any persistence changes. */
    @Transactional(readOnly = true)
    public CheckoutResponse validateCheckout(Long deliveryAddressId) {
        CheckoutResponse response = new CheckoutResponse();
        List<String> errors = new ArrayList<>();

        // Get the currently authenticated customer
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Customer account not found"));

        if (!user.isEnabled()) {
            errors.add("Customer account is inactive");
            response.setSuccess(false);
            response.setErrors(errors);
            return response;
        }

        // 1. Check cart is non-empty
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElse(null);

        if (cart == null) {
            errors.add("Cart is empty - no items to checkout");
            response.setSuccess(false);
            response.setErrors(errors);
            return response;
        }

        // Get cart items
        List<CartItem> cartItems = cartItemRepository.findByCartOrderByIdAsc(cart);

        if (cartItems.isEmpty()) {
            errors.add("Cart is empty - no items to checkout");
            response.setSuccess(false);
            response.setErrors(errors);
            return response;
        }

        // 2. Check selected delivery address exists and belongs to the authenticated customer
        Optional<Address> selectedAddressOpt = addressRepository.findById(deliveryAddressId);
        if (selectedAddressOpt.isEmpty()) {
            errors.add("Selected delivery address not found");
            response.setSuccess(false);
            response.setErrors(errors);
            return response;
        }

        Address selectedAddress = selectedAddressOpt.get();
        // Ownership check: address must belong to the authenticated customer
        if (!selectedAddress.getUser().getId().equals(user.getId())) {
            errors.add("Selected delivery address does not belong to your account");
            response.setSuccess(false);
            response.setErrors(errors);
            return response;
        }

        // 3. Validate every cart item's quantity and product status/stock
        for (CartItem item : cartItems) {
            Product product = item.getProduct();

            // Check product is active
            if (!product.isActive()) {
                errors.add("Product '" + product.getName() + "' is inactive and cannot be ordered");
                response.setSuccess(false);
                response.setErrors(errors);
                return response;
            }

            // Check quantity is valid (positive >= 1)
            Integer quantity = item.getQuantity();
            if (quantity == null || quantity < 1) {
                errors.add("Invalid quantity for product '" + product.getName() + "': quantity must be at least 1");
                response.setSuccess(false);
                response.setErrors(errors);
                return response;
            }

            // Check product has sufficient stock available
            int availableStock = product.getStockQuantity();
            if (availableStock < quantity) {
                errors.add("Insufficient stock for product '" + product.getName() + "': "
                        + "requested " + quantity + " but only " + availableStock + " available");
                response.setSuccess(false);
                response.setErrors(errors);
                return response;
            }
        }

        // All validations passed
        response.setSuccess(true);
        response.setErrors(List.of());

        // Include cart response with updated availability info
        CartResponse cartResponse = buildCartResponse(cart);
        response.setCart(cartResponse);

        return response;
    }

    /** Build a CartResponse from a Cart, similar to CartService.buildCartResponse. */
    private CartResponse buildCartResponse(Cart cart) {
        List<CartItem> cartItems = cartItemRepository.findByCartOrderByIdAsc(cart);

        List<mn.icode.dto.CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            boolean available = product.isActive() && product.getStockQuantity() >= item.getQuantity();
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

            mn.icode.dto.CartItemResponse itemResponse = new mn.icode.dto.CartItemResponse(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    item.getQuantity(),
                    product.getPrice(),
                    available,
                    lineTotal);

            itemResponses.add(itemResponse);
            subtotal = subtotal.add(lineTotal);
        }

        return new CartResponse(cart.getId(), itemResponses, subtotal);
    }
}