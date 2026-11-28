package mn.icode.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.entity.Order;
import mn.icode.entity.User;
import mn.icode.model.OrderStatus;
import mn.icode.model.Role;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminorders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminOrderSearchIntegrationTest {
    @Autowired AdminOrderService service;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;

    @Test
    void combinesCustomerStatusAndInclusiveDateFiltersWithStablePages() {
        User nora = customer("nora-search@example.com");
        User other = customer("other-search@example.com");
        Order first = order(nora, OrderStatus.PENDING);
        Order second = order(nora, OrderStatus.PENDING);
        order(nora, OrderStatus.SHIPPED);
        order(other, OrderStatus.PENDING);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        var page0 = service.list(OrderStatus.PENDING, " NORA-SEARCH@EXAMPLE.COM ",
                today, today, 0, 1);
        var page1 = service.list(OrderStatus.PENDING, "nora-search@example.com",
                today, today, 1, 1);
        assertEquals(2, page0.getTotalElements());
        assertEquals(second.getId(), page0.getContent().getFirst().id());
        assertEquals(first.getId(), page1.getContent().getFirst().id());
        assertTrue(service.list(null, "missing@example.com", null, null, 0, 20).isEmpty());
        assertTrue(service.list(null, null, today.minusDays(2), today.minusDays(1), 0, 20).isEmpty());
    }

    private User customer(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("Customer");
        user.setEmail(email);
        user.setPassword("test");
        user.setRole(Role.CUSTOMER);
        return users.save(user);
    }

    private Order order(User user, OrderStatus status) {
        Order order = new Order();
        order.setUser(user);
        order.setStatus(status);
        order.setShipCity("Ulaanbaatar");
        order.setShipDistrict("Sukhbaatar");
        order.setShipAddressLine("Peace Ave 1");
        order.setShipPhone("99112233");
        return orders.saveAndFlush(order);
    }
}
