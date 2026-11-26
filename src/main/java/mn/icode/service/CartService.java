package mn.icode.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.dto.CartItemResponse;
import mn.icode.dto.CartResponse;
import mn.icode.entity.Cart;
import mn.icode.entity.CartItem;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.Role;
import mn.icode.repository.CartItemRepository;
import mn.icode.repository.CartRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;

@Service 
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository, CartRepository cartRepository,
                       UserRepository userRepository, ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CartResponse updateItemQuantity(String email, long itemId, int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        User user = userRepository.findByEmailForUpdate(email)
            .orElseThrow(() -> new AccessDeniedException("Customer account not found"));
        if (!user.isEnabled() || user.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Customer account is inactive");
        }

        Cart cart = cartRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Cart item not found in this cart"));

        CartItem item = cartItemRepository.findByIdAndCart(itemId, cart)
            .orElseThrow(() -> new ResourceNotFoundException("Cart item not found in this cart"));

        Product product = item.getProduct();
        if (!product.isActive()) {
            throw new ConflictException("Product is unavailable");
        }

        if (newQuantity > product.getStockQuantity()) {
            throw new ConflictException("Requested quantity exceeds available stock");
        }

        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse deleteItem(String email, Long itemId) {
        User user = userRepository.findByEmailForUpdate(email)
            .orElseThrow(() -> new AccessDeniedException("Customer account not found"));

        if(!user.isEnabled() || user.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Customer account is inactive");
        }

        Cart cart = cartRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Cart item not found in this cart"));

        CartItem item = cartItemRepository.findByIdAndCart(itemId, cart)
            .orElseThrow(() -> new ResourceNotFoundException("Cart item not found in this cart"));
        
        cartItemRepository.delete(item);
        return buildCartResponse(cart);
    }

    @Transactional(readOnly=true)
    public CartResponse getCart(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AccessDeniedException("Customer account not found"));
        if (!user.isEnabled() || user.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Customer account is inactive");
        }

        return cartRepository.findByUserId(user.getId())
            .map(this::buildCartResponse)
            .orElseGet(() -> new CartResponse(null, List.of(), BigDecimal.ZERO));
    }

    @Transactional
    public CartResponse addItem(String email, long productId, int quantity) {
        if (productId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Product ID and quantity must be positive");
        }

        // Lock the customer row before finding or creating their cart. Concurrent additions
        // (including the first addition) are then serialized for this customer's cart.
        User user = userRepository.findByEmailForUpdate(email)
            .orElseThrow(() -> new AccessDeniedException("Customer account not found"));
        if (!user.isEnabled() || user.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Customer account is inactive");
        }

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!product.isActive() || product.getStockQuantity() == 0) {
            throw new ConflictException("Product is unavailable");
        }

        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
        CartItem item = cart == null ? null : cartItemRepository.findByCartAndProduct(cart, product).orElse(null);
        long total = (long) quantity + (item == null ? 0 : item.getQuantity());
        if (total > product.getStockQuantity() || total > Integer.MAX_VALUE) {
            throw new ConflictException("Requested quantity exceeds available stock");
        }

        if (cart == null) {
            cart = cartRepository.save(new Cart(user));
        }
        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
        }
        item.setQuantity((int) total);
        cartItemRepository.save(item);
        return buildCartResponse(cart);
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItemResponse> items = cartItemRepository
            .findByCartOrderByIdAsc(cart)
            .stream()
            .map(this::mapCartItem)
            .toList();
        
        BigDecimal subtotal = items.stream()
            .map(CartItemResponse::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), items, subtotal);
    }

    private CartItemResponse mapCartItem(CartItem item) {
        Product product = item.getProduct();
        boolean available = product.isActive() && product.getStockQuantity() >= item.getQuantity();

        BigDecimal unitPrice = product.getPrice();

        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

        return new CartItemResponse(item.getId(),
                                    product.getId(),
                                    product.getName(),
                                    item.getQuantity(),
                                    unitPrice,
                                    available,
                                    lineTotal);
    }
}
