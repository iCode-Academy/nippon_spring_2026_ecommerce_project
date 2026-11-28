package mn.icode.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import mn.icode.entity.Address;
import mn.icode.entity.Cart;
import mn.icode.entity.CartItem;
import mn.icode.entity.Category;
import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.OrderStatus;
import mn.icode.model.Role;
import mn.icode.repository.AddressRepository;
import mn.icode.repository.CartItemRepository;
import mn.icode.repository.CartRepository;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("postgres-test")
class CheckoutServiceIntegrationTest {

        @Autowired
        private CheckoutService checkoutService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private AddressRepository addressRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private CartRepository cartRepository;

        @MockitoSpyBean
        private CartItemRepository cartItemRepository;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private TransactionTemplate transactionTemplate;

        @PersistenceContext
        private EntityManager entityManager;

        private final List<Long> customerIds = new ArrayList<>();
        private final List<Long> productIds = new ArrayList<>();
        private final List<Long> categoryIds = new ArrayList<>();

        private User createCustomer() {
                User user = new User();

                user.setFirstName("Test");
                user.setLastName("Customer");
                user.setEmail(UUID.randomUUID() + "@example.com");
                user.setPassword("test-password");
                user.setRole(Role.CUSTOMER);
                user.setEnabled(true);

                User savedUser = userRepository.save(user);
                customerIds.add(savedUser.getId());

                return savedUser;
        }

        private Address createAddress(User user) {
                Address address = new Address();

                address.setUser(user);
                address.setTitle("Home");
                address.setCity("Ulaanbaatar");
                address.setDistrict("Bayanzurkh");
                address.setAddressLine("Test Street 123");
                address.setPhone("99112233");
                address.setDefaultAddress(true);

                return addressRepository.save(address);
        }

        private Product createProduct(
                        String name, String price, int stock, boolean active) {

                Category category = categoryRepository.save(
                                new Category("Checkout Test " + UUID.randomUUID()));

                categoryIds.add(category.getId());

                Product product = new Product(
                                category,
                                name,
                                "Test product description",
                                new BigDecimal(price),
                                stock,
                                active);

                Product savedProduct = productRepository.save(product);
                productIds.add(savedProduct.getId());

                return savedProduct;
        }

        private Cart createCart(User user) {
                return cartRepository.save(new Cart(user));
        }

        private CartItem createCartItem(
                        Cart cart, Product product, int quantity) {

                CartItem item = new CartItem();

                item.setCart(cart);
                item.setProduct(product);
                item.setQuantity(quantity);

                return cartItemRepository.save(item);
        }

        private void assertCheckoutUnchanged(
                        User customer, Cart cart, int expectedItemCount) {

                transactionTemplate.executeWithoutResult(status -> {

                        assertTrue(orderRepository
                                        .findByUserIdOrderByCreatedAtDesc(customer.getId())
                                        .isEmpty());

                        Cart remainingCart = cartRepository
                                        .findByUserId(customer.getId())
                                        .orElseThrow();

                        assertEquals(cart.getId(), remainingCart.getId());

                        assertEquals(expectedItemCount,
                                        cartItemRepository
                                                        .findByCartOrderByIdAsc(remainingCart)
                                                        .size());
                });
        }

        private void assertProductStock(Product product, int expectedStock) {
                transactionTemplate.executeWithoutResult(status -> {
                        Product persistedProduct = productRepository
                                        .findById(product.getId())
                                        .orElseThrow();

                        assertEquals(expectedStock, persistedProduct.getStockQuantity());
                });
        }

        private void assertCartItemUnchanged(
                        Cart cart, Product product, int expectedQuantity) {

                transactionTemplate.executeWithoutResult(status -> {

                        CartItem item = cartItemRepository
                                        .findByCartOrderByIdAsc(cart)
                                        .stream()
                                        .filter(cartItem -> cartItem.getProduct()
                                                        .getId().equals(product.getId()))
                                        .findFirst()
                                        .orElseThrow();

                        assertEquals(expectedQuantity, item.getQuantity());
                });
        }

        @AfterEach
        void cleanup() {

                transactionTemplate.executeWithoutResult(status -> {

                        for (Long customerId : customerIds) {

                                // Delete orders and their associated order items
                                orderRepository.deleteAll(
                                                orderRepository.findByUserIdOrderByCreatedAtDesc(customerId));

                                // Delete cart items and cart
                                cartRepository.findByUserId(customerId).ifPresent(cart -> {
                                        cartItemRepository.deleteAll(
                                                        cartItemRepository.findByCartOrderByIdAsc(cart));

                                        cartRepository.delete(cart);
                                });

                                // Delete customer addresses
                                addressRepository.deleteAll(
                                                addressRepository.findByUserId(customerId));
                        }

                        entityManager.flush();

                        // Delete customers
                        userRepository.deleteAllById(customerIds);
                        entityManager.flush();

                        // Delete products
                        productRepository.deleteAllById(productIds);
                        entityManager.flush();

                        // Delete categories
                        categoryRepository.deleteAllById(categoryIds);
                        entityManager.flush();
                });

                customerIds.clear();
                productIds.clear();
                categoryIds.clear();
        }

        @Test
        void checkout_createsOrderAndClearsCart() {

                // Arrange
                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product notebook = createProduct(
                                "Notebook", "12.50", 10, true);

                Product pen = createProduct(
                                "Pen", "3.00", 10, true);

                createCartItem(cart, notebook, 2);
                createCartItem(cart, pen, 3);

                // Act
                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                assertNotNull(createdOrder.getId());

                // Assert using fresh database records
                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        // Verify order details
                        assertEquals(customer.getId(), order.getUser().getId());
                        assertEquals(OrderStatus.PENDING, order.getStatus());
                        assertEquals(new BigDecimal("34.00"), order.getSubtotal());
                        assertEquals(new BigDecimal("34.00"), order.getTotal());

                        // Verify delivery address snapshot
                        assertEquals(address.getTitle(), order.getShipTitle());
                        assertEquals(address.getCity(), order.getShipCity());
                        assertEquals(address.getDistrict(), order.getShipDistrict());
                        assertEquals(address.getAddressLine(), order.getShipAddressLine());
                        assertEquals(address.getPhone(), order.getShipPhone());

                        // Verify order items
                        assertEquals(2, order.getItems().size());

                        OrderItem notebookItem = order.getItems().stream()
                                        .filter(item -> item.getProductId().equals(notebook.getId()))
                                        .findFirst()
                                        .orElseThrow();

                        assertEquals("Notebook", notebookItem.getProductName());
                        assertEquals(new BigDecimal("12.50"), notebookItem.getUnitPrice());
                        assertEquals(2, notebookItem.getQuantity());
                        assertEquals(new BigDecimal("25.00"), notebookItem.getLineTotal());

                        OrderItem penItem = order.getItems().stream()
                                        .filter(item -> item.getProductId().equals(pen.getId()))
                                        .findFirst()
                                        .orElseThrow();

                        assertEquals("Pen", penItem.getProductName());
                        assertEquals(new BigDecimal("3.00"), penItem.getUnitPrice());
                        assertEquals(3, penItem.getQuantity());
                        assertEquals(new BigDecimal("9.00"), penItem.getLineTotal());

                        // Verify cart record still exists
                        Cart remainingCart = cartRepository
                                        .findByUserId(customer.getId())
                                        .orElseThrow();

                        assertEquals(cart.getId(), remainingCart.getId());

                        // Verify cart items were cleared
                        assertTrue(cartItemRepository
                                        .findByCartOrderByIdAsc(remainingCart)
                                        .isEmpty());
                        assertEquals(8, productRepository.findById(notebook.getId()).orElseThrow().getStockQuantity());
                        assertEquals(7, productRepository.findById(pen.getId()).orElseThrow().getStockQuantity());
                });
        }

        @Test
        void checkout_onlyOneCustomerCanBuyTheLastUnit() throws Exception {
                User first = createCustomer();
                User second = createCustomer();
                Address firstAddress = createAddress(first);
                Address secondAddress = createAddress(second);
                Cart firstCart = createCart(first);
                Cart secondCart = createCart(second);
                Product product = createProduct("Last unit", "12.50", 1, true);
                createCartItem(firstCart, product, 1);
                createCartItem(secondCart, product, 1);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                        Future<Boolean> firstResult = executor
                                        .submit(() -> attemptCheckout(first, firstAddress, ready, start));
                        Future<Boolean> secondResult = executor
                                        .submit(() -> attemptCheckout(second, secondAddress, ready, start));
                        assertTrue(ready.await(10, TimeUnit.SECONDS));
                        start.countDown();
                        boolean firstSucceeded = firstResult.get(20, TimeUnit.SECONDS);
                        boolean secondSucceeded = secondResult.get(20, TimeUnit.SECONDS);
                        assertTrue(firstSucceeded ^ secondSucceeded);

                        transactionTemplate.executeWithoutResult(status -> {
                                assertEquals(0, productRepository.findById(product.getId()).orElseThrow()
                                                .getStockQuantity());
                                assertEquals(firstSucceeded ? 1 : 0,
                                                orderRepository.findByUserIdOrderByCreatedAtDesc(first.getId()).size());
                                assertEquals(secondSucceeded ? 1 : 0,
                                                orderRepository.findByUserIdOrderByCreatedAtDesc(second.getId())
                                                                .size());
                                assertEquals(firstSucceeded ? 0 : 1,
                                                cartItemRepository.findByCartOrderByIdAsc(firstCart).size());
                                assertEquals(secondSucceeded ? 0 : 1,
                                                cartItemRepository.findByCartOrderByIdAsc(secondCart).size());
                        });
                        // Verify the losing customer's cart remains unchanged
                        if (firstSucceeded) {
                                assertCartItemUnchanged(secondCart, product, 1);
                        } else {
                                assertCartItemUnchanged(firstCart, product, 1);
                        }
                } finally {
                        start.countDown();
                        executor.shutdownNow();
                }
        }

        @Test
        void checkout_oppositeCartOrderLocksProductsInTheSameOrder() throws Exception {
                User first = createCustomer();
                User second = createCustomer();
                Address firstAddress = createAddress(first);
                Address secondAddress = createAddress(second);
                Cart firstCart = createCart(first);
                Cart secondCart = createCart(second);
                Product smallerId = createProduct("First", "12.50", 2, true);
                Product largerId = createProduct("Second", "3.00", 2, true);
                createCartItem(firstCart, smallerId, 1);
                createCartItem(firstCart, largerId, 1);
                createCartItem(secondCart, largerId, 1);
                createCartItem(secondCart, smallerId, 1);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                        Future<Boolean> firstResult = executor
                                        .submit(() -> attemptCheckout(first, firstAddress, ready, start));
                        Future<Boolean> secondResult = executor
                                        .submit(() -> attemptCheckout(second, secondAddress, ready, start));
                        assertTrue(ready.await(10, TimeUnit.SECONDS));
                        start.countDown();
                        assertTrue(firstResult.get(20, TimeUnit.SECONDS));
                        assertTrue(secondResult.get(20, TimeUnit.SECONDS));
                        transactionTemplate.executeWithoutResult(status -> {

                                assertEquals(0, productRepository
                                                .findById(smallerId.getId())
                                                .orElseThrow()
                                                .getStockQuantity());

                                assertEquals(0, productRepository
                                                .findById(largerId.getId())
                                                .orElseThrow()
                                                .getStockQuantity());

                                // Both customers must have exactly one order
                                assertEquals(1, orderRepository
                                                .findByUserIdOrderByCreatedAtDesc(first.getId())
                                                .size());

                                assertEquals(1, orderRepository
                                                .findByUserIdOrderByCreatedAtDesc(second.getId())
                                                .size());

                                // Both carts must be cleared
                                assertTrue(cartItemRepository
                                                .findByCartOrderByIdAsc(firstCart)
                                                .isEmpty());

                                assertTrue(cartItemRepository
                                                .findByCartOrderByIdAsc(secondCart)
                                                .isEmpty());
                        });
                } finally {
                        start.countDown();
                        executor.shutdownNow();
                }
        }

        private boolean attemptCheckout(User customer, Address address,
                        CountDownLatch ready, CountDownLatch start) throws InterruptedException {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent checkout did not start");
                }
                try {
                        checkoutService.checkout(customer.getEmail(), address.getId());
                        return true;
                } catch (ConflictException expected) {
                        return false;
                }
        }

        @Test
        void checkout_failedSecondProductRestoresEarlierStockAndCart() {
                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);
                Product first = createProduct("Available", "12.50", 5, true);
                Product second = createProduct("Sold out", "3.00", 1, true);
                // Reverse cart insertion order: product locks must still follow product IDs.
                createCartItem(cart, second, 2);
                createCartItem(cart, first, 2);

                assertThrows(ConflictException.class,
                                () -> checkoutService.checkout(customer.getEmail(), address.getId()));

                assertCheckoutUnchanged(customer, cart, 2);
                transactionTemplate.executeWithoutResult(status -> {
                        assertEquals(5, productRepository.findById(first.getId()).orElseThrow().getStockQuantity());
                        assertEquals(1, productRepository.findById(second.getId()).orElseThrow().getStockQuantity());
                });
                assertCartItemUnchanged(cart, first, 2);
                assertCartItemUnchanged(cart, second, 2);
        }

        @Test
        void checkout_preservesOtherCustomersCartItems() {

                // Arrange: first customer
                User firstCustomer = createCustomer();
                Address firstAddress = createAddress(firstCustomer);
                Cart firstCart = createCart(firstCustomer);

                // Arrange: second customer
                User secondCustomer = createCustomer();
                Cart secondCart = createCart(secondCustomer);

                // Create a shared product
                Product product = createProduct(
                                "Notebook", "12.50", 20, true);

                CartItem firstItem = createCartItem(firstCart, product, 2);
                CartItem secondItem = createCartItem(secondCart, product, 3);

                // Act: only the first customer checks out
                Order order = checkoutService.checkout(
                                firstCustomer.getEmail(),
                                firstAddress.getId());

                // Assert
                transactionTemplate.executeWithoutResult(status -> {

                        assertNotNull(order.getId());

                        // First customer's cart item was deleted
                        assertFalse(cartItemRepository.existsById(firstItem.getId()));

                        // Second customer's cart item still exists
                        CartItem remainingItem = cartItemRepository
                                        .findById(secondItem.getId())
                                        .orElseThrow();

                        assertEquals(3, remainingItem.getQuantity());
                        assertEquals(product.getId(), remainingItem.getProduct().getId());
                        assertEquals(secondCart.getId(), remainingItem.getCart().getId());

                        // Verify only the first customer has an order
                        assertEquals(1, orderRepository
                                        .findByUserIdOrderByCreatedAtDesc(firstCustomer.getId())
                                        .size());

                        assertTrue(orderRepository
                                        .findByUserIdOrderByCreatedAtDesc(secondCustomer.getId())
                                        .isEmpty());
                });
        }

        @Test
        void checkout_rejectsCustomerWithoutCart() {

                User customer = createCustomer();
                Address address = createAddress(customer);

                assertThrows(ConflictException.class, () -> checkoutService.checkout(
                                customer.getEmail(), address.getId()));

                assertTrue(orderRepository
                                .findByUserIdOrderByCreatedAtDesc(customer.getId())
                                .isEmpty());

                assertTrue(cartRepository
                                .findByUserId(customer.getId())
                                .isEmpty());
        }

        @Test
        void checkout_rejectsEmptyCart() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                assertThrows(ConflictException.class, () -> checkoutService.checkout(
                                customer.getEmail(), address.getId()));

                assertCheckoutUnchanged(customer, cart, 0);
        }

        @Test
        void checkout_rejectsAnotherCustomersAddress() {

                User customer = createCustomer();
                User anotherCustomer = createCustomer();

                Address foreignAddress = createAddress(anotherCustomer);

                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                assertThrows(ResourceNotFoundException.class, () -> checkoutService.checkout(
                                customer.getEmail(), foreignAddress.getId()));

                assertCheckoutUnchanged(customer, cart, 1);

                assertProductStock(product, 10);
                assertCartItemUnchanged(cart, product, 2);
        }

        @Test
        void checkout_rejectsNonexistentAddress() {

                User customer = createCustomer();
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                assertThrows(ResourceNotFoundException.class, () -> checkoutService.checkout(
                                customer.getEmail(), Long.MAX_VALUE));

                assertCheckoutUnchanged(customer, cart, 1);
        }

        @Test
        void checkout_rejectsInvalidDeliveryAddressId() {

                User customer = createCustomer();
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                assertThrows(IllegalArgumentException.class, () -> checkoutService.checkout(customer.getEmail(), null));

                assertThrows(IllegalArgumentException.class, () -> checkoutService.checkout(customer.getEmail(), 0L));

                assertThrows(IllegalArgumentException.class, () -> checkoutService.checkout(customer.getEmail(), -1L));

                assertCheckoutUnchanged(customer, cart, 1);
        }

        @Test
        void checkout_rejectsIncompleteDeliveryAddress() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                // Remove required shipping information
                address.setPhone(null);
                addressRepository.save(address);

                ConflictException exception = assertThrows(
                                ConflictException.class, () -> checkoutService.checkout(
                                                customer.getEmail(), address.getId()));

                assertEquals(
                                "Delivery address must include a phone number",
                                exception.getMessage());

                assertCheckoutUnchanged(customer, cart, 1);
        }

        @Test
        void checkout_rejectsInactiveProduct() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product activeProduct = createProduct(
                                "Notebook", "12.50", 10, true);

                Product inactiveProduct = createProduct(
                                "Pen", "3.00", 10, false);

                createCartItem(cart, activeProduct, 2);
                createCartItem(cart, inactiveProduct, 3);

                assertThrows(ConflictException.class, () -> checkoutService.checkout(
                                customer.getEmail(), address.getId()));

                assertCheckoutUnchanged(customer, cart, 2);
                assertProductStock(activeProduct, 10);
                assertProductStock(inactiveProduct, 10);

                assertCartItemUnchanged(cart, activeProduct, 2);
                assertCartItemUnchanged(cart, inactiveProduct, 3);
        }

        @Test
        void checkout_rejectsInsufficientStock() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 2, true);

                createCartItem(cart, product, 5);

                assertThrows(ConflictException.class, () -> checkoutService.checkout(
                                customer.getEmail(), address.getId()));

                assertCheckoutUnchanged(customer, cart, 1);
        }

        @Test
        void checkout_rejectsDisabledCustomer() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                customer.setEnabled(false);
                userRepository.save(customer);

                assertThrows(AccessDeniedException.class, () -> checkoutService.checkout(
                                customer.getEmail(), address.getId()));

                assertCheckoutUnchanged(customer, cart, 1);
        }

        @Test
        void checkout_rejectsAdminAccount() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                customer.setRole(Role.ADMIN);
                userRepository.save(customer);

                assertThrows(AccessDeniedException.class, () -> checkoutService.checkout(
                                customer.getEmail(), address.getId()));

                assertCheckoutUnchanged(customer, cart, 1);
        }

        @Test
        void checkout_rejectsNonexistentCustomer() {

                assertThrows(AccessDeniedException.class, () -> checkoutService.checkout(
                                UUID.randomUUID() + "@example.com", 1L));
        }

        @Test
        void checkout_usesCurrentDatabasePrice() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                // Change product price after adding it to the cart
                product.setPrice(new BigDecimal("15.00"));
                productRepository.save(product);

                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        OrderItem item = order.getItems().get(0);

                        assertEquals(new BigDecimal("15.00"), item.getUnitPrice());
                        assertEquals(new BigDecimal("30.00"), item.getLineTotal());
                        assertEquals(new BigDecimal("30.00"), order.getSubtotal());
                        assertEquals(new BigDecimal("30.00"), order.getTotal());
                });
                assertProductStock(product, 8);

                transactionTemplate.executeWithoutResult(status -> {
                        assertTrue(cartItemRepository
                                        .findByCartOrderByIdAsc(cart)
                                        .isEmpty());
                });
        }

        @Test
        void checkout_acceptsQuantityEqualToStock() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 5, true);

                createCartItem(cart, product, 5);

                Order order = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                assertNotNull(order.getId());

                assertEquals(new BigDecimal("62.50"), order.getTotal());

                assertFalse(orderRepository
                                .findByUserIdOrderByCreatedAtDesc(customer.getId())
                                .isEmpty());

                assertTrue(cartItemRepository
                                .findByCartOrderByIdAsc(cart)
                                .isEmpty());
        }

        @Test
        void checkout_preservesProductSnapshotAfterProductUpdate() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Original Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                // Update the original product
                product.setName("Updated Notebook");
                product.setPrice(new BigDecimal("20.00"));
                productRepository.save(product);

                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        OrderItem item = order.getItems().get(0);

                        assertEquals(product.getId(), item.getProductId());
                        assertEquals("Original Notebook", item.getProductName());
                        assertEquals(new BigDecimal("12.50"), item.getUnitPrice());
                        assertEquals(2, item.getQuantity());
                        assertEquals(new BigDecimal("25.00"), item.getLineTotal());
                        assertEquals(new BigDecimal("25.00"), order.getTotal());
                });
        }

        @Test
        void checkout_preservesAddressSnapshotAfterAddressUpdate() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                // Update the original address
                address.setTitle("Office");
                address.setCity("Darkhan");
                address.setDistrict("New District");
                address.setAddressLine("New Street 456");
                address.setPhone("88112233");

                addressRepository.save(address);

                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        assertEquals("Home", order.getShipTitle());
                        assertEquals("Ulaanbaatar", order.getShipCity());
                        assertEquals("Bayanzurkh", order.getShipDistrict());
                        assertEquals("Test Street 123", order.getShipAddressLine());
                        assertEquals("99112233", order.getShipPhone());
                });
        }

        @Test
        void checkout_preservesAddressSnapshotAfterAddressDeletion() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                addressRepository.deleteById(address.getId());

                assertFalse(addressRepository.existsById(address.getId()));

                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        assertEquals("Home", order.getShipTitle());
                        assertEquals("Ulaanbaatar", order.getShipCity());
                        assertEquals("Bayanzurkh", order.getShipDistrict());
                        assertEquals("Test Street 123", order.getShipAddressLine());
                        assertEquals("99112233", order.getShipPhone());
                });
        }

        @Test
        void checkout_rejectsRepeatedCheckoutAfterCartCleared() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                // First checkout succeeds
                Order firstOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                assertNotNull(firstOrder.getId());

                // Second checkout fails because the cart is empty
                assertThrows(ConflictException.class, () -> checkoutService.checkout(
                                customer.getEmail(), address.getId()));

                transactionTemplate.executeWithoutResult(status -> {

                        List<Order> orders = orderRepository
                                        .findByUserIdOrderByCreatedAtDesc(customer.getId());

                        assertEquals(1, orders.size());
                        assertEquals(firstOrder.getId(), orders.get(0).getId());

                        assertTrue(cartItemRepository
                                        .findByCartOrderByIdAsc(cart)
                                        .isEmpty());
                });
        }

        @Test
        void checkout_rollsBackWhenOrderPersistenceFails() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Expensive Product",
                                "6000000000.00",
                                10,
                                true);

                CartItem cartItem = createCartItem(cart, product, 2);

                assertThrows(DataIntegrityViolationException.class, () -> checkoutService.checkout(
                                customer.getEmail(), address.getId()));

                assertCheckoutUnchanged(customer, cart, 1);
                assertProductStock(product, 10);

                transactionTemplate.executeWithoutResult(status -> {

                        CartItem remainingItem = cartItemRepository
                                        .findById(cartItem.getId())
                                        .orElseThrow();

                        assertEquals(2, remainingItem.getQuantity());
                        assertEquals(product.getId(),
                                        remainingItem.getProduct().getId());
                });
        }

        @Test
        void checkout_rollsBackOrderAndCartDeletionWhenTransactionFails() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                CartItem cartItem = createCartItem(cart, product, 2);

                assertThrows(IllegalStateException.class, () ->

                transactionTemplate.executeWithoutResult(status -> {

                        Order order = checkoutService.checkout(
                                        customer.getEmail(), address.getId());

                        // Force pending SQL operations to execute
                        entityManager.flush();
                        entityManager.clear();

                        // Verify order exists inside the transaction
                        assertTrue(orderRepository.existsById(order.getId()));

                        // Verify cart item was deleted
                        assertFalse(cartItemRepository
                                        .existsById(cartItem.getId()));

                        // Verify stock was decremented
                        assertEquals(8, productRepository
                                        .findById(product.getId())
                                        .orElseThrow()
                                        .getStockQuantity());

                        // Simulate a failure before transaction commit
                        throw new IllegalStateException(
                                        "Simulated transaction failure");
                }));

                // Verify order and cart rollback
                assertCheckoutUnchanged(customer, cart, 1);

                // Verify inventory rollback
                assertProductStock(product, 10);

                // Verify original cart item was restored
                transactionTemplate.executeWithoutResult(status -> {

                        CartItem restoredItem = cartItemRepository
                                        .findById(cartItem.getId())
                                        .orElseThrow();

                        assertEquals(2, restoredItem.getQuantity());
                        assertEquals(product.getId(),
                                        restoredItem.getProduct().getId());
                });
        }

        @Test
        void checkout_rollsBackWhenCartDeletionFails() {

                // Arrange
                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                CartItem cartItem = createCartItem(cart, product, 2);

                doAnswer(invocation -> {

                        List<CartItem> items = invocation.getArgument(0);

                        for (CartItem item : items) {
                                cartItemRepository.delete(item);
                        }

                        entityManager.flush();

                        throw new IllegalStateException(
                                        "Simulated cart deletion failure");

                }).when(cartItemRepository).deleteAll(anyList());

                try {

                        // Act
                        IllegalStateException exception = assertThrows(
                                        IllegalStateException.class, () -> checkoutService.checkout(
                                                        customer.getEmail(), address.getId()));

                        assertEquals(
                                        "Simulated cart deletion failure",
                                        exception.getMessage());

                } finally {

                        // Remove simulated failure before cleanup
                        reset(cartItemRepository);
                }

                // Assert
                assertCheckoutUnchanged(customer, cart, 1);
                assertEquals(10, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());

                transactionTemplate.executeWithoutResult(status -> {

                        CartItem restoredItem = cartItemRepository
                                        .findById(cartItem.getId())
                                        .orElseThrow();

                        assertEquals(2, restoredItem.getQuantity());
                        assertEquals(product.getId(),
                                        restoredItem.getProduct().getId());
                });
        }

        @Test
        void checkout_preventsModificationOfPersistedAddressSnapshot() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        assertThrows(IllegalStateException.class,
                                        () -> order.setShipTitle("Office"));

                        assertThrows(IllegalStateException.class,
                                        () -> order.setShipCity("Darkhan"));

                        assertThrows(IllegalStateException.class,
                                        () -> order.setShipDistrict("New District"));

                        assertThrows(IllegalStateException.class,
                                        () -> order.setShipAddressLine("New Address"));

                        assertThrows(IllegalStateException.class,
                                        () -> order.setShipPhone("88112233"));

                        assertEquals("Home", order.getShipTitle());
                        assertEquals("Ulaanbaatar", order.getShipCity());
                        assertEquals("Bayanzurkh", order.getShipDistrict());
                        assertEquals("Test Street 123", order.getShipAddressLine());
                        assertEquals("99112233", order.getShipPhone());
                });
        }

        @Test
        void checkout_preventsChangingPersistedOrderOwner() {

                User customer = createCustomer();
                User anotherCustomer = createCustomer();

                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        assertThrows(IllegalStateException.class,
                                        () -> order.setUser(anotherCustomer));

                        assertEquals(customer.getId(), order.getUser().getId());
                });
        }

        @Test
        void checkout_preventsModificationOfPersistedItemsAndTotals() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        OrderItem additionalItem = OrderItem.from(product, 3);

                        // Cannot add another item
                        assertThrows(IllegalStateException.class,
                                        () -> order.addItem(additionalItem));

                        // Cannot remove existing items
                        assertThrows(UnsupportedOperationException.class,
                                        () -> order.getItems().clear());

                        // Cannot modify the collection directly
                        assertThrows(UnsupportedOperationException.class,
                                        () -> order.getItems().add(additionalItem));

                        // Cannot recalculate persisted totals
                        assertThrows(IllegalStateException.class,
                                        () -> order.recalcTotals());

                        assertEquals(1, order.getItems().size());
                        assertEquals(new BigDecimal("25.00"), order.getSubtotal());
                        assertEquals(new BigDecimal("25.00"), order.getTotal());
                });
        }

        @Test
        void checkout_allowsOrderStatusUpdateWithoutChangingSnapshots() {

                User customer = createCustomer();
                Address address = createAddress(customer);
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                Order createdOrder = checkoutService.checkout(
                                customer.getEmail(), address.getId());

                // Update order status in a separate transaction
                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        assertEquals(OrderStatus.PENDING, order.getStatus());

                        order.setStatus(OrderStatus.CONFIRMED);

                        orderRepository.saveAndFlush(order);
                });

                // Retrieve fresh records from PostgreSQL
                transactionTemplate.executeWithoutResult(status -> {

                        Order order = orderRepository
                                        .findById(createdOrder.getId())
                                        .orElseThrow();

                        assertEquals(OrderStatus.CONFIRMED, order.getStatus());

                        // Verify totals remain unchanged
                        assertEquals(new BigDecimal("25.00"), order.getSubtotal());
                        assertEquals(new BigDecimal("25.00"), order.getTotal());

                        // Verify shipping snapshot remains unchanged
                        assertEquals("Home", order.getShipTitle());
                        assertEquals("Ulaanbaatar", order.getShipCity());
                        assertEquals("Bayanzurkh", order.getShipDistrict());
                        assertEquals("Test Street 123", order.getShipAddressLine());
                        assertEquals("99112233", order.getShipPhone());

                        // Verify product snapshot remains unchanged
                        assertEquals(1, order.getItems().size());

                        OrderItem item = order.getItems().get(0);

                        assertEquals("Notebook", item.getProductName());
                        assertEquals(new BigDecimal("12.50"), item.getUnitPrice());
                        assertEquals(2, item.getQuantity());
                        assertEquals(new BigDecimal("25.00"), item.getLineTotal());
                });
        }

}
