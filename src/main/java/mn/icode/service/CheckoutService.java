package mn.icode.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.entity.Address;
import mn.icode.entity.Cart;
import mn.icode.entity.CartItem;
import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.Role;
import mn.icode.repository.AddressRepository;
import mn.icode.repository.CartItemRepository;
import mn.icode.repository.CartRepository;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.UserRepository;

@Service
public class CheckoutService {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;

    public CheckoutService(AddressRepository addressRepository, CartItemRepository cartItemRepository,
            CartRepository cartRepository, OrderRepository orderRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Order checkout(String email, Long deliveryAddressId) {

        if (deliveryAddressId == null || deliveryAddressId <= 0) {
            throw new IllegalArgumentException("Delivery address is required");
        }
        // Retrieve and lock customer
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new AccessDeniedException("Customer account not found"));

        if (!user.isEnabled() || user.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Customer account is inactive or unauthorized");
        }

        // Retrieve the customer's cart
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ConflictException("Cart is empty"));

        List<CartItem> cartItems = cartItemRepository.findByCartOrderByIdAsc(cart);

        if (cartItems.isEmpty()) {
            throw new ConflictException("Cart is empty");
        }

        // Retrieve delivery address
        Address address = addressRepository.findByIdAndUserId(deliveryAddressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery address not found"));

        validateDeliveryAddress(address);
        validateCartItems(cartItems);

        // Create order and shipping address snapshot
        Order order = createOrder(user, address);

        // Create order item snapshots
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = OrderItem.from(
                    cartItem.getProduct(),
                    cartItem.getQuantity());
            order.addItem(orderItem);
        }

        // Calculate serverside totals
        order.recalcTotals();

        // Persist order and order items
        Order savedOrder = orderRepository.save(order);

        // Clear purchased cart items
        cartItemRepository.deleteAll(cartItems);

        // Return the created order
        return savedOrder;
    }

    private void validateDeliveryAddress(Address address) {

        if (address.getCity() == null || address.getCity().isBlank()) {
            throw new ConflictException("Delivery address must include a city");
        }

        if (address.getDistrict() == null || address.getDistrict().isBlank()) {
            throw new ConflictException("Delivery address must include a district");
        }

        if (address.getAddressLine() == null || address.getAddressLine().isBlank()) {
            throw new ConflictException("Delivery address must include an address line");
        }

        if (address.getPhone() == null || address.getPhone().isBlank()) {
            throw new ConflictException("Delivery address must include a phone number");
        }
    }

    private void validateCartItems(List<CartItem> cartItems) {

        for (CartItem item : cartItems) {

            Product product = item.getProduct();
            Integer quantity = item.getQuantity();

            if (!product.isActive()) {
                throw new ConflictException(
                        "Product '" + product.getName() + "' is unavailable");
            }

            if (quantity == null || quantity <= 0) {
                throw new ConflictException(
                        "Invalid quantity for product '" + product.getName() + "'");
            }

            if (quantity > product.getStockQuantity()) {
                throw new ConflictException(
                        "Insufficient stock for product '" + product.getName() + "'");
            }
        }
    }

    private Order createOrder(User user, Address address) {

        Order order = new Order();

        order.setUser(user);

        // Create shipping address snapshot
        order.setShipTitle(address.getTitle());
        order.setShipCity(address.getCity());
        order.setShipDistrict(address.getDistrict());
        order.setShipAddressLine(address.getAddressLine());
        order.setShipPhone(address.getPhone());

        return order;
    }
}
