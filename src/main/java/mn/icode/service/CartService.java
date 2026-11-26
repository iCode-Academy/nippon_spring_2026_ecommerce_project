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
import mn.icode.repository.CartItemRepository;
import mn.icode.repository.CartRepository;
import mn.icode.repository.UserRepository;

@Service 
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;

    public CartService(CartItemRepository cartItemRepository, CartRepository cartRepository, UserRepository userRepository) {
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly=true)
    public CartResponse getCart(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AccessDeniedException("Customer account not found"));

        return cartRepository.findByUserId(user.getId())
            .map(this::buildCartResponse)
            .orElseGet(() -> new CartResponse(null, List.of(), BigDecimal.ZERO));
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
