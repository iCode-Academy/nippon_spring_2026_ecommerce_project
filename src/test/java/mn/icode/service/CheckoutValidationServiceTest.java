package mn.icode.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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

@ExtendWith(MockitoExtension.class)
class CheckoutValidationServiceTest {

    private CheckoutValidationService validationService;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    private User customer;

    @BeforeEach
    void setUp() {
        validationService = new CheckoutValidationService(
                addressRepository,
                cartItemRepository,
                cartRepository,
                productRepository,
                userRepository
        );

        customer = new User();
        customer.setId(1L);
        customer.setEmail("nora@example.com");
        customer.setEnabled(true);
        customer.setRole(Role.CUSTOMER);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nora@example.com", "password", List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CartItem createCartItem(Cart cart, String name, boolean active, int stock, int quantity, BigDecimal price) {
        Product product = org.mockito.Mockito.mock(Product.class);
        org.mockito.Mockito.lenient().when(product.getId()).thenReturn(10L);
        org.mockito.Mockito.lenient().when(product.getName()).thenReturn(name);
        org.mockito.Mockito.lenient().when(product.isActive()).thenReturn(active);
        org.mockito.Mockito.lenient().when(product.getStockQuantity()).thenReturn(stock);
        org.mockito.Mockito.lenient().when(product.getPrice()).thenReturn(price);

        CartItem item = new CartItem();
        item.setId(100L);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private Address createOwnedAddress(User owner, Long addressId) {
        Address address = new Address();
        address.setId(addressId);
        address.setUser(owner);
        address.setCity("Ulaanbaatar");
        address.setDistrict("Sukhbaatar");
        address.setAddressLine("Peace Ave 1");
        address.setPhone("99112233");
        return address;
    }

    @Test
    void validateCheckout_emptyCart_returnsError() {
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CheckoutResponse result = validationService.validateCheckout(1L);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertEquals("Cart is empty - no items to checkout", result.getErrors().get(0));
    }

    @Test
    void validateCheckout_foreignAddress_returnsError() {
        Cart cart = new Cart(customer);
        cart.setId(1L);
        CartItem item = createCartItem(cart, "Valid Product", true, 10, 1, new BigDecimal("10.00"));

        User otherUser = new User();
        otherUser.setId(2L);
        Address foreignAddress = createOwnedAddress(otherUser, 99L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartOrderByIdAsc(cart)).thenReturn(List.of(item));
        when(addressRepository.findById(99L)).thenReturn(Optional.of(foreignAddress));

        CheckoutResponse result = validationService.validateCheckout(99L);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertEquals("Selected delivery address does not belong to your account", result.getErrors().get(0));
    }

    @Test
    void validateCheckout_incompleteOwnedAddress_returnsFieldErrorsBeforeCheckingProducts() {
        Cart cart = new Cart(customer);
        cart.setId(1L);
        CartItem item = createCartItem(cart, "Inactive Product", false, 10, 1, new BigDecimal("10.00"));
        Address ownedAddress = createOwnedAddress(customer, 1L);
        ownedAddress.setCity(" ");
        ownedAddress.setDistrict(null);
        ownedAddress.setAddressLine("\t");
        ownedAddress.setPhone(null);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartOrderByIdAsc(cart)).thenReturn(List.of(item));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(ownedAddress));

        CheckoutResponse result = validationService.validateCheckout(1L);

        assertFalse(result.isSuccess());
        assertEquals(List.of(
                "Delivery address must include a city",
                "Delivery address must include a district",
                "Delivery address must include an address line",
                "Delivery address must include a phone number"), result.getErrors());
        verify(cartRepository, never()).save(any());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void validateCheckout_inactiveProduct_returnsError() {
        Cart cart = new Cart(customer);
        cart.setId(1L);
        CartItem item = createCartItem(cart, "Inactive Product", false, 10, 1, new BigDecimal("10.00"));
        Address ownedAddress = createOwnedAddress(customer, 1L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartOrderByIdAsc(cart)).thenReturn(List.of(item));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(ownedAddress));

        CheckoutResponse result = validationService.validateCheckout(1L);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("inactive"));
    }

    @Test
    void validateCheckout_insufficientStock_returnsError() {
        Cart cart = new Cart(customer);
        cart.setId(1L);
        CartItem item = createCartItem(cart, "Low Stock Product", true, 2, 5, new BigDecimal("10.00"));
        Address ownedAddress = createOwnedAddress(customer, 1L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartOrderByIdAsc(cart)).thenReturn(List.of(item));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(ownedAddress));

        CheckoutResponse result = validationService.validateCheckout(1L);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("Insufficient stock"));
    }

    @Test
    void validateCheckout_badQuantity_returnsError() {
        Cart cart = new Cart(customer);
        cart.setId(1L);
        CartItem item = createCartItem(cart, "Test Product", true, 10, 0, new BigDecimal("10.00"));
        Address ownedAddress = createOwnedAddress(customer, 1L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartOrderByIdAsc(cart)).thenReturn(List.of(item));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(ownedAddress));

        CheckoutResponse result = validationService.validateCheckout(1L);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("must be at least 1"));
    }

    @Test
    void validateCheckout_validCheckout_returnsSuccess() {
        Cart cart = new Cart(customer);
        cart.setId(1L);
        CartItem item = createCartItem(cart, "Valid Product", true, 5, 3, new BigDecimal("15.00"));
        Address ownedAddress = createOwnedAddress(customer, 1L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartOrderByIdAsc(cart)).thenReturn(List.of(item));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(ownedAddress));

        CheckoutResponse result = validationService.validateCheckout(1L);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getErrors().size());
        assertNotNull(result.getCart());
    }

    @Test
    void validateCheckout_noPersistenceOnFailure() {
        Cart cart = new Cart(customer);
        cart.setId(1L);
        CartItem item = createCartItem(cart, "Inactive Product", false, 10, 1, new BigDecimal("10.00"));
        Address ownedAddress = createOwnedAddress(customer, 1L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartOrderByIdAsc(cart)).thenReturn(List.of(item));
        when(addressRepository.findById(1L)).thenReturn(Optional.of(ownedAddress));

        CheckoutResponse result = validationService.validateCheckout(1L);

        assertFalse(result.isSuccess());
        verify(cartRepository, never()).save(any());
        verify(cartItemRepository, never()).save(any());
        verify(productRepository, never()).save(any());
        verify(addressRepository, never()).save(any());
    }
}
