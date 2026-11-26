package mn.icode.controller;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import mn.icode.entity.Cart;
import mn.icode.entity.CartItem;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.model.Role;
import mn.icode.repository.CartItemRepository;
import mn.icode.repository.CartRepository;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres_test")
@Transactional
class CartApiControllerTest {

        private static final String ITEMS_URL = "/api/cart/items";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private CartRepository cartRepository;

        @Autowired
        private CartItemRepository cartItemRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private EntityManager entityManager;

        private User createCustomer(String email) {

                User customer = new User();

                customer.setFirstName("Test");
                customer.setLastName("Customer");
                customer.setEmail(email);
                customer.setPassword("test-password");
                customer.setRole(Role.CUSTOMER);
                customer.setEnabled(true);

                return userRepository.save(customer);
        }

        private Cart createCart(User customer) {
                return cartRepository.save(new Cart(customer));
        }

        private Product createProduct(
                        String name,
                        String price,
                        int stock,
                        boolean active) {

                Category category = categoryRepository.save(
                                new Category(name + " Test Category"));

                return productRepository.save(
                                new Product(
                                                category,
                                                name,
                                                "Test description",
                                                new BigDecimal(price),
                                                stock,
                                                active));
        }

        private CartItem createCartItem(
                        Cart cart,
                        Product product,
                        int quantity) {

                CartItem item = new CartItem();

                item.setCart(cart);
                item.setProduct(product);
                item.setQuantity(quantity);

                return cartItemRepository.save(item);
        }

        private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder addItem(
                        long productId, int quantity) {
                return post(ITEMS_URL).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + "}");
        }

        @Test
        void firstAdditionCreatesCartAndReturnsUpdatedCart() throws Exception {
                User customer = createCustomer("first-add@example.com");
                Product product = createProduct("First notebook", "12.50", 3, true);

                mockMvc.perform(addItem(product.getId(), 2).with(user(customer.getEmail()).roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].quantity").value(2))
                                .andExpect(jsonPath("$.items[0].available").value(true))
                                .andExpect(jsonPath("$.subtotal").value(25.00));
                assertEquals(1, cartRepository.count());
                assertEquals(1, cartItemRepository.count());
                assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        }

        @Test
        void duplicateAdditionMergesAndRejectsExcessWithoutChangingCart() throws Exception {
                User customer = createCustomer("merge-add@example.com");
                Product product = createProduct("Merge notebook", "8.00", 3, true);
                var customerAuth = user(customer.getEmail()).roles("CUSTOMER");

                mockMvc.perform(addItem(product.getId(), 1).with(customerAuth)).andExpect(status().isOk());
                mockMvc.perform(addItem(product.getId(), 2).with(user(customer.getEmail()).roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].quantity").value(3))
                                .andExpect(jsonPath("$.subtotal").value(24.00));
                mockMvc.perform(addItem(product.getId(), 1).with(user(customer.getEmail()).roles("CUSTOMER")))
                                .andExpect(status().isConflict());
                assertEquals(1, cartItemRepository.count());
                assertEquals(3, cartItemRepository.findAll().getFirst().getQuantity());
        }

        @Test
        void invalidOrMissingFieldsDoNotCreateCart() throws Exception {
                User customer = createCustomer("invalid-add@example.com");
                for (String body : new String[] {
                                "{}", "{\"productId\":1}", "{\"quantity\":1}", "{",
                                "{\"productId\":0,\"quantity\":1}",
                                "{\"productId\":1,\"quantity\":0}",
                                "{\"productId\":1,\"quantity\":-2}",
                                "{\"productId\":1,\"quantity\":2147483648}" }) {
                        mockMvc.perform(post(ITEMS_URL).with(csrf()).with(user(customer.getEmail()).roles("CUSTOMER"))
                                        .contentType(MediaType.APPLICATION_JSON).content(body))
                                        .andExpect(status().isBadRequest());
                }
                assertEquals(0, cartRepository.count());
        }

        @Test
        void unavailableAndMissingProductsDoNotCreateCart() throws Exception {
                User customer = createCustomer("unavailable-add@example.com");
                Product inactive = createProduct("Inactive notebook", "10.00", 10, false);
                Product empty = createProduct("Empty notebook", "10.00", 0, true);
                Product limited = createProduct("Limited notebook", "10.00", 1, true);

                for (long id : new long[] { inactive.getId(), empty.getId() }) {
                        mockMvc.perform(addItem(id, 1).with(user(customer.getEmail()).roles("CUSTOMER")))
                                        .andExpect(status().isConflict());
                }
                mockMvc.perform(addItem(limited.getId(), 2).with(user(customer.getEmail()).roles("CUSTOMER")))
                                .andExpect(status().isConflict());
                mockMvc.perform(addItem(9999999, 1).with(user(customer.getEmail()).roles("CUSTOMER")))
                                .andExpect(status().isNotFound());
                assertEquals(0, cartRepository.count());
        }

        @Test
        void postRequiresCustomerAccountAndCsrf() throws Exception {
                User customer = createCustomer("secured-add@example.com");
                User inactive = createCustomer("inactive-add@example.com");
                inactive.setEnabled(false);
                userRepository.save(inactive);
                Product product = createProduct("Secure notebook", "10.00", 5, true);

                mockMvc.perform(addItem(product.getId(), 1))
                                .andExpect(status().is3xxRedirection());
                mockMvc.perform(addItem(product.getId(), 1).with(user("admin@example.com").roles("ADMIN")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post(ITEMS_URL).with(user(customer.getEmail()).roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":" + product.getId() + ",\"quantity\":1}"))
                                .andExpect(status().isForbidden());
                mockMvc.perform(addItem(product.getId(), 1).with(user(inactive.getEmail()).roles("CUSTOMER")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(addItem(product.getId(), 1).with(user("deleted@example.com").roles("CUSTOMER")))
                                .andExpect(status().isForbidden());
                assertFalse(cartRepository.existsByUserId(customer.getId()));
        }

        @Test
        void authenticatedCustomerCanReadOwnCart() throws Exception {

                // Arrange
                User customer = createCustomer("cart-a@example.com");
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                CartItem item = createCartItem(cart, product, 2);

                // Act & Assert
                mockMvc.perform(get("/api/cart")
                                .with(user(customer.getEmail())
                                                .roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.cartId").value(cart.getId()))
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].id").value(item.getId()))
                                .andExpect(jsonPath("$.items[0].productId")
                                                .value(product.getId()))
                                .andExpect(jsonPath("$.items[0].productName")
                                                .value("Notebook"))
                                .andExpect(jsonPath("$.items[0].quantity").value(2))
                                .andExpect(jsonPath("$.items[0].unitPrice").value(12.50))
                                .andExpect(jsonPath("$.items[0].available").value(true))
                                .andExpect(jsonPath("$.items[0].lineTotal").value(25.00))
                                .andExpect(jsonPath("$.subtotal").value(25.00));
        }

        @Test
        void unauthenticatedUserCannotReadCart() throws Exception {

                mockMvc.perform(get("/api/cart"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(header().string("Location", "/login"));
        }

        @Test
        void adminCannotReadCustomerCart() throws Exception {

                mockMvc.perform(get("/api/cart")
                                .with(user("admin@example.com")
                                                .roles("ADMIN")))
                                .andExpect(status().isForbidden());
        }

        @Test
        void customerWithoutCartReceivesEmptyResponse() throws Exception {

                User customer = createCustomer("empty-cart@example.com");

                mockMvc.perform(get("/api/cart")
                                .with(user(customer.getEmail())
                                                .roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.cartId").doesNotExist())
                                .andExpect(jsonPath("$.items").isEmpty())
                                .andExpect(jsonPath("$.subtotal").value(0));
        }

        @Test
        void customerCannotRequestAnotherUsersCart() throws Exception {

                // Arrange: Customer A
                User customerA = createCustomer("owner-a@example.com");
                Cart cartA = createCart(customerA);

                Product notebook = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cartA, notebook, 2);

                // Arrange: Customer B
                User customerB = createCustomer("owner-b@example.com");
                Cart cartB = createCart(customerB);

                Product bag = createProduct(
                                "Bag", "40.00", 10, true);

                createCartItem(cartB, bag, 1);

                // Act: Customer A supplies Customer B's IDs
                mockMvc.perform(get("/api/cart")
                                .param("userId", customerB.getId().toString())
                                .param("cartId", cartB.getId().toString())
                                .with(user(customerA.getEmail())
                                                .roles("CUSTOMER")))

                                // Assert: Only Customer A's cart is returned
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.cartId").value(cartA.getId()))
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].productName")
                                                .value("Notebook"))
                                .andExpect(jsonPath("$.subtotal").value(25.00));
        }

        @Test
        void cartUsesCurrentDatabaseProductPrice() throws Exception {

                // Arrange
                User customer = createCustomer("price-test@example.com");
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 10, true);

                createCartItem(cart, product, 2);

                entityManager.flush();
                entityManager.clear();

                // Verify original price
                mockMvc.perform(get("/api/cart")
                                .with(user(customer.getEmail())
                                                .roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.subtotal").value(25.00));

                // Update the database price
                Product updatedProduct = productRepository
                                .findById(product.getId())
                                .orElseThrow();

                updatedProduct.setPrice(new BigDecimal("15.00"));

                productRepository.saveAndFlush(updatedProduct);
                entityManager.clear();

                // Verify recalculated subtotal
                mockMvc.perform(get("/api/cart")
                                .with(user(customer.getEmail())
                                                .roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[0].unitPrice").value(15.00))
                                .andExpect(jsonPath("$.items[0].lineTotal").value(30.00))
                                .andExpect(jsonPath("$.subtotal").value(30.00));
        }

        @Test
        void cartDisplaysUnavailableProducts() throws Exception {

                User customer = createCustomer("availability@example.com");
                Cart cart = createCart(customer);

                Product product = createProduct(
                                "Notebook", "12.50", 1, true);

                createCartItem(cart, product, 2);

                mockMvc.perform(get("/api/cart")
                                .with(user(customer.getEmail())
                                                .roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].available").value(false))
                                .andExpect(jsonPath("$.subtotal").value(25.00));
        }

        @Test
        void authenticatedCustomerWithoutAccountReceivesForbidden()
                        throws Exception {

                mockMvc.perform(get("/api/cart")
                                .with(user("nonexistent@example.com")
                                                .roles("CUSTOMER")))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.status").value(403))
                                .andExpect(jsonPath("$.title").value("Forbidden"))
                                .andExpect(jsonPath("$.detail").value(
                                                "You do not have permission to access this resource."));
        }
}
