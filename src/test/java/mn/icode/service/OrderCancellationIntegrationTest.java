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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import mn.icode.entity.Address;
import mn.icode.entity.Cart;
import mn.icode.entity.CartItem;
import mn.icode.entity.Category;
import mn.icode.entity.Order;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.exception.StaleOrderUpdateException;
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
class OrderCancellationIntegrationTest {

    @Autowired
    private OrderService orderService;

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

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AdminOrderService adminOrderService;

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
                new Category("Cancellation Test " + UUID.randomUUID()));

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

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();

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

    private void authenticate(User customer) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        customer.getEmail(), "test-password", List.of()));
    }

    private Order createOrder(User customer, Product product, int quantity) {
        Address address = createAddress(customer);
        Cart cart = createCart(customer);
        createCartItem(cart, product, quantity);
        return checkoutService.checkout(customer.getEmail(), address.getId());
    }

    private int getStock(Long productId) {
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }

    private OrderStatus getOrderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private void updateOrderStatus(Long orderId, OrderStatus orderStatus) {
        transactionTemplate
                .executeWithoutResult(status -> orderRepository.findById(orderId).orElseThrow().setStatus(orderStatus));
    }

    private void maximizeStock(Long productId) {
        transactionTemplate.executeWithoutResult(status -> productRepository.findById(productId).orElseThrow()
                .setStockQuantity(Integer.MAX_VALUE));
    }

    @Test
    void cancelOrder_restoresAllProductsAndPersistsCancelledStatus() {
        User customer = createCustomer();
        Address address = createAddress(customer);
        Cart cart = createCart(customer);
        Product notebook = createProduct("Notebook", "12.50", 10, true);
        Product pen = createProduct("Pen", "3.00", 10, true);
        createCartItem(cart, notebook, 2);
        createCartItem(cart, pen, 3);
        Order order = checkoutService.checkout(customer.getEmail(), address.getId());
        assertEquals(8, getStock(notebook.getId()));
        assertEquals(7, getStock(pen.getId()));
        assertEquals(OrderStatus.PENDING, getOrderStatus(order.getId()));
        authenticate(customer);
        orderService.cancelOrder(order.getId());
        assertEquals(10, getStock(notebook.getId()));
        assertEquals(10, getStock(pen.getId()));
        assertEquals(OrderStatus.CANCELLED, getOrderStatus(order.getId()));
        transactionTemplate.executeWithoutResult(status -> {
            Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(2, cancelled.getItems().size());
            assertEquals(new BigDecimal("34.00"), cancelled.getSubtotal());
            assertEquals(new BigDecimal("34.00"), cancelled.getTotal());
            assertEquals("Test Street 123", cancelled.getShipAddressLine());
            assertTrue(cartItemRepository.findByCartOrderByIdAsc(cart).isEmpty());
        });
    }

    @Test
    void cancelOrder_confirmedOrder_restoresStock() {
        User customer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(customer, product, 2);
        updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);
        assertEquals(8, getStock(product.getId()));
        authenticate(customer);
        orderService.cancelOrder(order.getId());
        assertEquals(10, getStock(product.getId()));
        assertEquals(OrderStatus.CANCELLED, getOrderStatus(order.getId()));
    }

    @Test
    void cancelOrder_repeatedRequest_doesNotRestoreStockTwice() {
        User customer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(customer, product, 2);
        authenticate(customer);
        assertEquals(8, getStock(product.getId()));
        orderService.cancelOrder(order.getId());
        assertEquals(10, getStock(product.getId()));
        assertEquals(OrderStatus.CANCELLED, getOrderStatus(order.getId()));
        assertThrows(ConflictException.class, () -> orderService.cancelOrder(order.getId()));
        assertEquals(10, getStock(product.getId()));
        assertEquals(OrderStatus.CANCELLED, getOrderStatus(order.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "SHIPPED", "DELIVERED" })
    void cancelOrder_lateStatus_rejectsWithoutRestoringStock(OrderStatus status) {
        User customer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(customer, product, 2);
        updateOrderStatus(order.getId(), status);
        authenticate(customer);
        assertEquals(8, getStock(product.getId()));
        assertThrows(ConflictException.class, () -> orderService.cancelOrder(order.getId()));
        assertEquals(8, getStock(product.getId()));
        assertEquals(status, getOrderStatus(order.getId()));
    }

    @Test
    void cancelOrder_anotherCustomersOrder_rejectsCancellation() {
        User owner = createCustomer();
        User anotherCustomer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(owner, product, 2);
        assertEquals(8, getStock(product.getId()));
        authenticate(anotherCustomer);
        assertThrows(ResourceNotFoundException.class, () -> orderService.cancelOrder(order.getId()));
        assertEquals(8, getStock(product.getId()));
        assertEquals(OrderStatus.PENDING, getOrderStatus(order.getId()));
    }

    @Test
    void cancelOrder_inactiveProduct_restoresStockWithoutReactivation() {
        User customer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(customer, product, 2);
        transactionTemplate.executeWithoutResult(
                status -> productRepository.findById(product.getId()).orElseThrow().setActive(false));
        assertEquals(8, getStock(product.getId()));
        authenticate(customer);
        orderService.cancelOrder(order.getId());
        assertEquals(10, getStock(product.getId()));
        assertEquals(OrderStatus.CANCELLED, getOrderStatus(order.getId()));
        assertFalse(productRepository.findById(product.getId()).orElseThrow().isActive());
    }

    @Test
    void cancelOrder_missingProduct_rejectsCancellation() {
        User customer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(customer, product, 2);
        productRepository.deleteById(product.getId());
        assertFalse(productRepository.existsById(product.getId()));
        authenticate(customer);
        assertThrows(ConflictException.class, () -> orderService.cancelOrder(order.getId()));
        assertEquals(OrderStatus.PENDING, getOrderStatus(order.getId()));
        assertFalse(productRepository.existsById(product.getId()));
    }

    @Test
    void cancelOrder_stockOverflow_rejectsCancellation() {
        User customer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(customer, product, 2);
        maximizeStock(product.getId());
        authenticate(customer);
        assertThrows(ConflictException.class, () -> orderService.cancelOrder(order.getId()));
        assertEquals(Integer.MAX_VALUE, getStock(product.getId()));
        assertEquals(OrderStatus.PENDING, getOrderStatus(order.getId()));
    }

    @Test
    void cancelOrder_failedSecondProduct_rollsBackEarlierRestoration() {
        User customer = createCustomer();
        Address address = createAddress(customer);
        Cart cart = createCart(customer);
        Product first = createProduct("Notebook", "12.50", 10, true);
        Product second = createProduct("Pen", "3.00", 10, true);
        assertTrue(first.getId() < second.getId());
        createCartItem(cart, second, 3);
        createCartItem(cart, first, 2);
        Order order = checkoutService.checkout(customer.getEmail(), address.getId());
        assertEquals(8, getStock(first.getId()));
        assertEquals(7, getStock(second.getId()));
        maximizeStock(second.getId());
        authenticate(customer);
        assertThrows(ConflictException.class, () -> orderService.cancelOrder(order.getId()));
        assertEquals(8, getStock(first.getId()));
        assertEquals(Integer.MAX_VALUE, getStock(second.getId()));
        assertEquals(OrderStatus.PENDING, getOrderStatus(order.getId()));
    }

    @Test
    void cancelOrder_concurrentRequests_restoreStockOnlyOnce() throws Exception {
        User customer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(customer, product, 2);
        assertEquals(8, getStock(product.getId()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> attemptCancellation(customer, order.getId(), ready, start));
            Future<Boolean> second = executor.submit(() -> attemptCancellation(customer, order.getId(), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean firstSucceeded = first.get(20, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(20, TimeUnit.SECONDS);
            assertTrue(firstSucceeded ^ secondSucceeded);
            assertEquals(10, getStock(product.getId()));
            assertEquals(OrderStatus.CANCELLED, getOrderStatus(order.getId()));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private boolean attemptCancellation(User customer, Long orderId,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        try {
            authenticate(customer);
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent cancellation did not start");
            }
            try {
                orderService.cancelOrder(orderId);
                return true;
            } catch (ConflictException expected) {
                return false;
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void cancellationWins_shippingIsRejected() throws Exception {
        verifyCancellationShippingRace(true);
    }

    @Test
    void shippingWins_cancellationIsRejected() throws Exception {
        verifyCancellationShippingRace(false);
    }

    @Test
    void cancellationAndShipping_simultaneousRequests_onlyOneSucceeds()
            throws Exception {

        User customer = createCustomer();
        Product product = createProduct("Notebook", "12.50", 10, true);
        Order order = createOrder(customer, product, 2);

        adminOrderService.updateStatus(
                order.getId(),
                OrderStatus.CONFIRMED,
                OrderStatus.PENDING);

        assertEquals(8, getStock(product.getId()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> cancellation = executor.submit(() -> attemptCancellation(
                    customer, order.getId(), ready, start));

            Future<Boolean> shipping = executor.submit(() -> {
                ready.countDown();
                awaitRaceLatch(start);

                return attemptShipping(order.getId());
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean cancellationSucceeded = cancellation.get(20, TimeUnit.SECONDS);

            boolean shippingSucceeded = shipping.get(20, TimeUnit.SECONDS);

            // Exactly one operation must succeed
            assertTrue(cancellationSucceeded ^ shippingSucceeded);

            assertRaceFinalState(
                    customer, product, order.getId(),
                    cancellationSucceeded);

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(executor.awaitTermination(
                    20, TimeUnit.SECONDS));
        }
    }

    private void verifyCancellationShippingRace(
            boolean cancellationWins) throws Exception {

        User customer = createCustomer();

        Product product = createProduct(
                "Notebook", "12.50", 10, true);

        Order order = createOrder(customer, product, 2);

        Long orderId = order.getId();

        adminOrderService.updateStatus(
                orderId,
                OrderStatus.CONFIRMED,
                OrderStatus.PENDING);

        assertEquals(8, getStock(product.getId()));
        assertEquals(OrderStatus.CONFIRMED, getOrderStatus(orderId));

        CountDownLatch winnerHoldingLock = new CountDownLatch(1);
        CountDownLatch loserStarted = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> winner = executor.submit(() -> {

                try {
                    authenticate(customer);

                    transactionTemplate.executeWithoutResult(status -> {

                        if (cancellationWins) {
                            orderService.cancelOrder(orderId);
                        } else {
                            adminOrderService.updateStatus(
                                    orderId,
                                    OrderStatus.SHIPPED,
                                    OrderStatus.CONFIRMED);
                        }

                        entityManager.flush();

                        // Transaction remains open and holds the lock
                        winnerHoldingLock.countDown();

                        awaitRaceLatch(releaseWinner);
                    });

                    return true;

                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            assertTrue(winnerHoldingLock.await(
                    10, TimeUnit.SECONDS));

            Future<Boolean> loser = executor.submit(() -> {

                try {
                    authenticate(customer);

                    loserStarted.countDown();

                    if (cancellationWins) {
                        return attemptShipping(orderId);
                    }

                    try {
                        orderService.cancelOrder(orderId);
                        return true;

                    } catch (ConflictException expected) {
                        return false;
                    }

                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            assertTrue(loserStarted.await(10, TimeUnit.SECONDS));

            // Allow the winning transaction to commit
            releaseWinner.countDown();

            assertTrue(winner.get(20, TimeUnit.SECONDS));

            // The competing operation must be rejected
            assertFalse(loser.get(20, TimeUnit.SECONDS));

            assertRaceFinalState(
                    customer, product, orderId, cancellationWins);

        } finally {
            releaseWinner.countDown();
            executor.shutdownNow();

            assertTrue(executor.awaitTermination(
                    20, TimeUnit.SECONDS));
        }
    }

    private boolean attemptShipping(Long orderId) {

        try {
            adminOrderService.updateStatus(
                    orderId,
                    OrderStatus.SHIPPED,
                    OrderStatus.CONFIRMED);

            return true;

        } catch (StaleOrderUpdateException expected) {
            return false;
        }
    }

    private void awaitRaceLatch(CountDownLatch latch) {

        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Concurrent operation timed out");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Concurrent operation interrupted", e);
        }
    }

    private void assertRaceFinalState(
            User customer,
            Product product,
            Long orderId,
            boolean cancellationSucceeded) {

        transactionTemplate.executeWithoutResult(status -> {

            Order persistedOrder = orderRepository
                    .findById(orderId)
                    .orElseThrow();

            Product persistedProduct = productRepository
                    .findById(product.getId())
                    .orElseThrow();

            // Verify final order status
            assertEquals(
                    cancellationSucceeded
                            ? OrderStatus.CANCELLED
                            : OrderStatus.SHIPPED,
                    persistedOrder.getStatus());

            // Stock is restored only if cancellation wins
            assertEquals(
                    cancellationSucceeded ? 10 : 8,
                    persistedProduct.getStockQuantity());

            // Exactly one order remains
            assertEquals(1, orderRepository
                    .findByUserIdOrderByCreatedAtDesc(customer.getId())
                    .size());

            // Order ownership remains unchanged
            assertEquals(
                    customer.getId(),
                    persistedOrder.getUser().getId());

            // Original order items remain unchanged
            assertEquals(1, persistedOrder.getItems().size());

            var item = persistedOrder.getItems().getFirst();

            assertEquals(product.getId(), item.getProductId());
            assertEquals("Notebook", item.getProductName());
            assertEquals(2, item.getQuantity());

            assertEquals(
                    new BigDecimal("12.50"),
                    item.getUnitPrice());

            assertEquals(
                    new BigDecimal("25.00"),
                    item.getLineTotal());

            // Original totals remain unchanged
            assertEquals(
                    new BigDecimal("25.00"),
                    persistedOrder.getSubtotal());

            assertEquals(
                    new BigDecimal("25.00"),
                    persistedOrder.getTotal());

            // Delivery address snapshot remains unchanged
            assertEquals("Home", persistedOrder.getShipTitle());
            assertEquals("Ulaanbaatar", persistedOrder.getShipCity());
            assertEquals("Bayanzurkh", persistedOrder.getShipDistrict());

            assertEquals(
                    "Test Street 123",
                    persistedOrder.getShipAddressLine());

            assertEquals("99112233", persistedOrder.getShipPhone());

            // Checkout cart remains empty
            Cart cart = cartRepository
                    .findByUserId(customer.getId())
                    .orElseThrow();

            assertTrue(cartItemRepository
                    .findByCartOrderByIdAsc(cart)
                    .isEmpty());
        });
    }
}
