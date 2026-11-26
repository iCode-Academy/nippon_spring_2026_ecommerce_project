package mn.icode.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import mn.icode.dto.CartResponse;
import mn.icode.entity.Cart;
import mn.icode.entity.CartItem;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.repository.CartItemRepository;
import mn.icode.repository.CartRepository;
import mn.icode.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
    public class CartServiceTest {
    
        @Mock
        private CartRepository cartRepository;
    
        @Mock
        private CartItemRepository cartItemRepository;
    
        @Mock
        private UserRepository userRepository;
    
        @InjectMocks
        private CartService cartService;
    
        private User user;
        private Cart cart;
        private Category category;
    
        private final String email = "customer@example.com";
    
        @BeforeEach
        void setUp() {
            user = mock(User.class);
    
            cart = new Cart(user);
            cart.setId(10L);
    
            category = new Category("Stationery");
        }
    
        private void mockCustomer() {
            when(user.getId()).thenReturn(1L);
    
            when(userRepository.findByEmail(email))
                    .thenReturn(Optional.of(user));
        }
    
        private void mockExistingCart() {
            mockCustomer();
    
            when(cartRepository.findByUserId(1L))
                    .thenReturn(Optional.of(cart));
        }
    
        private CartItem createCartItem(
                Long id,
                String name,
                String price,
                int stock,
                boolean active,
                int quantity) {
    
            Product product = new Product(
                    category,
                    name,
                    "Test description",
                    new BigDecimal(price),
                    stock,
                    active
            );
    
            CartItem item = new CartItem();
            item.setId(id);
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);
    
            return item;
        }
    
        @Test
        void getCart_returnsOwnedCartAndCorrectSubtotal() {
    
            // Arrange
            mockExistingCart();
    
            CartItem notebook = createCartItem(
                    1L, "Notebook", "12.50", 10, true, 2);
    
            CartItem pen = createCartItem(
                    2L, "Pen", "3.00", 20, true, 3);
    
            when(cartItemRepository.findByCartOrderByIdAsc(cart))
                    .thenReturn(List.of(notebook, pen));
    
            // Act
            CartResponse result = cartService.getCart(email);
    
            // Assert
            assertEquals(10L, result.cartId());
            assertEquals(2, result.items().size());
    
            assertEquals("Notebook",
                    result.items().get(0).productName());
    
            assertEquals(new BigDecimal("12.50"),
                    result.items().get(0).unitPrice());
    
            assertEquals(new BigDecimal("25.00"),
                    result.items().get(0).lineTotal());
    
            assertEquals(new BigDecimal("9.00"),
                    result.items().get(1).lineTotal());
    
            assertEquals(new BigDecimal("34.00"),
                    result.subtotal());
    
            assertTrue(result.items().get(0).available());
            assertTrue(result.items().get(1).available());
    
            verify(userRepository).findByEmail(email);
            verify(cartRepository).findByUserId(1L);
            verify(cartItemRepository).findByCartOrderByIdAsc(cart);
        }
    
        @Test
        void getCart_returnsEmptyResponseWhenCartDoesNotExist() {
    
            // Arrange
            mockCustomer();
    
            when(cartRepository.findByUserId(1L))
                    .thenReturn(Optional.empty());
    
            // Act
            CartResponse result = cartService.getCart(email);
    
            // Assert
            assertNull(result.cartId());
            assertTrue(result.items().isEmpty());
            assertEquals(BigDecimal.ZERO, result.subtotal());
    
            verifyNoInteractions(cartItemRepository);
            verify(cartRepository, never()).save(any(Cart.class));
        }
    
        @Test
        void getCart_returnsEmptyExistingCart() {
    
            // Arrange
            mockExistingCart();
    
            when(cartItemRepository.findByCartOrderByIdAsc(cart))
                    .thenReturn(List.of());
    
            // Act
            CartResponse result = cartService.getCart(email);
    
            // Assert
            assertEquals(10L, result.cartId());
            assertTrue(result.items().isEmpty());
            assertEquals(BigDecimal.ZERO, result.subtotal());
        }
    
        @Test
        void getCart_marksInactiveProductUnavailable() {
    
            // Arrange
            mockExistingCart();
    
            CartItem item = createCartItem(
                    1L, "Notebook", "12.50", 10, false, 2);
    
            when(cartItemRepository.findByCartOrderByIdAsc(cart))
                    .thenReturn(List.of(item));
    
            // Act
            CartResponse result = cartService.getCart(email);
    
            // Assert
            assertFalse(result.items().get(0).available());
            assertEquals(new BigDecimal("25.00"), result.subtotal());
        }
    
        @Test
        void getCart_marksInsufficientStockUnavailable() {
    
            // Arrange
            mockExistingCart();
    
            CartItem item = createCartItem(
                    1L, "Notebook", "12.50", 1, true, 2);
    
            when(cartItemRepository.findByCartOrderByIdAsc(cart))
                    .thenReturn(List.of(item));
    
            // Act
            CartResponse result = cartService.getCart(email);
    
            // Assert
            assertFalse(result.items().get(0).available());
        }
    
        @Test
        void getCart_marksExactStockQuantityAvailable() {
    
            // Arrange
            mockExistingCart();
    
            CartItem item = createCartItem(
                    1L, "Notebook", "12.50", 2, true, 2);
    
            when(cartItemRepository.findByCartOrderByIdAsc(cart))
                    .thenReturn(List.of(item));
    
            // Act
            CartResponse result = cartService.getCart(email);
    
            // Assert
            assertTrue(result.items().get(0).available());
        }
    
        @Test
        void getCart_usesCurrentProductPrice() {
    
            // Arrange
            mockExistingCart();
    
            CartItem item = createCartItem(
                    1L, "Notebook", "12.50", 10, true, 2);
    
            when(cartItemRepository.findByCartOrderByIdAsc(cart))
                    .thenReturn(List.of(item));
    
            // Act
            CartResponse before = cartService.getCart(email);
    
            item.getProduct().setPrice(new BigDecimal("15.00"));
    
            CartResponse after = cartService.getCart(email);
    
            // Assert
            assertEquals(new BigDecimal("25.00"), before.subtotal());
            assertEquals(new BigDecimal("30.00"), after.subtotal());
    
            assertEquals(new BigDecimal("15.00"),
                    after.items().get(0).unitPrice());
        }
    
        @Test
        void getCart_throwsExceptionWhenUserDoesNotExist() {
    
            // Arrange
            when(userRepository.findByEmail(email))
                    .thenReturn(Optional.empty());
    
            // Act & Assert
            assertThrows(
                    AccessDeniedException.class,
                    () -> cartService.getCart(email)
            );
    
            verifyNoInteractions(cartRepository, cartItemRepository);
        }
    }