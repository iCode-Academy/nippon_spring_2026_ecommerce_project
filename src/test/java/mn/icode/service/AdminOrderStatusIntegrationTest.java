package mn.icode.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import mn.icode.entity.Category;
import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.exception.InvalidOrderTransitionException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.exception.StaleOrderUpdateException;
import mn.icode.model.OrderStatus;
import mn.icode.model.Role;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminorderstatus;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminOrderStatusIntegrationTest {

    @Autowired AdminOrderService service;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;

    @PersistenceContext EntityManager entityManager;

    @Test
    void allowlistedForwardLifecyclePersistsEachStep() {
        Order order = createOrder(OrderStatus.PENDING);

        assertEquals("CONFIRMED", service.updateStatus(order.getId(),
                OrderStatus.CONFIRMED, OrderStatus.PENDING).status());
        assertEquals("SHIPPED", service.updateStatus(order.getId(),
                OrderStatus.SHIPPED, OrderStatus.CONFIRMED).status());
        assertEquals("DELIVERED", service.updateStatus(order.getId(),
                OrderStatus.DELIVERED, OrderStatus.SHIPPED).status());

        entityManager.flush();
        entityManager.clear();
        assertEquals(OrderStatus.DELIVERED,
                orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminCancellationRestoresStockAndPersistsCancelledStatus() {
        Order order = createOrder(OrderStatus.PENDING);
        Long productId = order.getItems().getFirst().getProductId();
        assertEquals(8, products.findById(productId).orElseThrow().getStockQuantity());

        var detail = service.updateStatus(order.getId(),
                OrderStatus.CANCELLED, OrderStatus.PENDING);

        assertEquals("CANCELLED", detail.status());
        entityManager.flush();
        entityManager.clear();
        assertEquals(OrderStatus.CANCELLED,
                orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(10, products.findById(productId).orElseThrow().getStockQuantity());
    }

    @Test
    void adminCancellationFromConfirmedRestoresStock() {
        Order order = createOrder(OrderStatus.CONFIRMED);
        Long productId = order.getItems().getFirst().getProductId();

        service.updateStatus(order.getId(), OrderStatus.CANCELLED, OrderStatus.CONFIRMED);

        entityManager.flush();
        entityManager.clear();
        assertEquals(10, products.findById(productId).orElseThrow().getStockQuantity());
    }

    @Test
    void staleExpectedStatusIsRejectedWithoutChangingStockOrStatus() {
        Order order = createOrder(OrderStatus.PENDING);
        Long productId = order.getItems().getFirst().getProductId();
        service.updateStatus(order.getId(), OrderStatus.CONFIRMED, OrderStatus.PENDING);

        assertThrows(StaleOrderUpdateException.class,
                () -> service.updateStatus(order.getId(),
                        OrderStatus.CANCELLED, OrderStatus.PENDING));

        entityManager.flush();
        entityManager.clear();
        assertEquals(OrderStatus.CONFIRMED,
                orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(8, products.findById(productId).orElseThrow().getStockQuantity());
    }

    @Test
    void arbitraryBackwardAndTerminalTransitionsAreRejected() {
        Order pending = createOrder(OrderStatus.PENDING);
        assertThrows(InvalidOrderTransitionException.class,
                () -> service.updateStatus(pending.getId(),
                        OrderStatus.SHIPPED, OrderStatus.PENDING));

        Order shipped = createOrder(OrderStatus.SHIPPED);
        assertThrows(InvalidOrderTransitionException.class,
                () -> service.updateStatus(shipped.getId(),
                        OrderStatus.CONFIRMED, OrderStatus.SHIPPED));

        Order delivered = createOrder(OrderStatus.DELIVERED);
        assertThrows(InvalidOrderTransitionException.class,
                () -> service.updateStatus(delivered.getId(),
                        OrderStatus.SHIPPED, OrderStatus.DELIVERED));

        entityManager.flush();
        entityManager.clear();
        assertEquals(OrderStatus.PENDING,
                orders.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.SHIPPED,
                orders.findById(shipped.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.DELIVERED,
                orders.findById(delivered.getId()).orElseThrow().getStatus());
    }

    @Test
    void missingOrderIsReportedAsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateStatus(999_999L,
                        OrderStatus.CONFIRMED, OrderStatus.PENDING));
    }

    private Order createOrder(OrderStatus status) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("Customer");
        user.setEmail(java.util.UUID.randomUUID() + "@example.com");
        user.setPassword("test-password");
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        users.save(user);

        Category category = categories.save(new Category(
                "Admin status test " + java.util.UUID.randomUUID()));
        Product product = products.save(new Product(category, "Notebook",
                "Test product description", new BigDecimal("12.50"), 8, true));

        Order order = new Order();
        order.setUser(user);
        order.setShipCity("Ulaanbaatar");
        order.setShipDistrict("Sukhbaatar");
        order.setShipAddressLine("Peace Ave 1");
        order.setShipPhone("99112233");
        order.addItem(OrderItem.from(product, 2));
        order.recalcTotals();
        order.setStatus(status);
        return orders.saveAndFlush(order);
    }
}
