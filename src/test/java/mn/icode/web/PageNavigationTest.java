package mn.icode.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.AdminPageController;
import mn.icode.controller.PageController;
import mn.icode.entity.Category;
import mn.icode.entity.Order;
import mn.icode.entity.Product;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.model.OrderStatus;
import mn.icode.model.Role;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;
import mn.icode.service.UserService;

@WebMvcTest(controllers = {PageController.class, AdminPageController.class})
@Import(SecurityConfig.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class PageNavigationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    private static User customer() {
        User user = new User();
        user.setId(1L);
        user.setFirstName("Nora");
        user.setLastName("Test");
        user.setEmail("nora@example.com");
        user.setPassword("secret");
        user.setRole(Role.CUSTOMER);
        return user;
    }

    // --- Navigation differs by audience ---

    @Test
    void anonymousUsersSeePublicNavigation() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Sign in")))
                .andExpect(content().string(containsString("Register")))
                .andExpect(content().string(not(containsString("/admin"))));
    }

    @Test
    void anonymousUsersAreSentToLoginForProtectedPages() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void customersSeeCustomerNavigationWithoutAdminLink() throws Exception {
        when(productRepository.findByActiveTrue()).thenReturn(List.of());

        mockMvc.perform(get("/home").with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("My orders")))
                .andExpect(content().string(containsString("Sign out")))
                .andExpect(content().string(not(containsString("/admin"))));
    }

    @Test
    void customersCannotOpenAdminPages() throws Exception {
        mockMvc.perform(get("/admin").with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsSeeAdminNavigation() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/admin").with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(content().string(containsString("href=\"/admin\"")))
                .andExpect(content().string(containsString("Admin dashboard")));
    }

    // --- Failure and empty states ---

    @Test
    void shopShowsEmptyStateWhenNoProducts() throws Exception {
        when(productRepository.findByActiveTrue()).thenReturn(List.of());

        mockMvc.perform(get("/home").with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No products yet")));
    }

    @Test
    void shopShowsProductCardsWhenProductsExist() throws Exception {
        Product product = new Product(new Category("Stationery"), "Fountain Pen",
                "Smooth writing", new BigDecimal("12.50"), 5, true);
        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));

        mockMvc.perform(get("/home").with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fountain Pen")))
                .andExpect(content().string(containsString("Stationery")));
    }

    @Test
    void ordersShowsEmptyStateWhenCustomerHasNoOrders() throws Exception {
        when(userRepository.findByEmail("nora@example.com"))
                .thenReturn(Optional.of(customer()));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        mockMvc.perform(get("/orders").with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No orders yet")));
    }

    @Test
    void ordersShowsTableRowsWhenOrdersExist() throws Exception {
        Order order = new Order();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(order, "total",
                new BigDecimal("19.99"));
        org.springframework.test.util.ReflectionTestUtils.setField(order, "subtotal",
                new BigDecimal("19.99"));
        order.setUser(customer());
        order.setStatus(OrderStatus.PENDING);

        when(userRepository.findByEmail("nora@example.com"))
                .thenReturn(Optional.of(customer()));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(order));

        mockMvc.perform(get("/orders").with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#7")))
                .andExpect(content().string(containsString("PENDING")));
    }

    @Test
    void adminDashboardShowsEmptyStateWhenNoUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/admin").with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No users yet")));
    }

    @Test
    void adminDashboardShowsUsersTableWhenUsersExist() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(customer()));
        when(userRepository.count()).thenReturn(1L);
        when(orderRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/admin").with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nora@example.com")));
    }

    @Test
    void loginShowsFailureMessageOnErrorFlag() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(
                        containsString("Invalid email or password")));
    }

    @Test
    void loginShowsLogoutMessageOnLogoutFlag() throws Exception {
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("signed out")));
    }

    // --- Registration form (flash messages, validation, conflict) ---

    @Test
    void validRegistrationRedirectsToLoginWithFlashSuccess() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "Nora")
                        .param("lastName", "Test")
                        .param("email", "nora@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("flashSuccess",
                        containsString("created")));

        verify(userService).registerUser(any(User.class));
    }

    @Test
    void invalidRegistrationReRendersFormWithFieldErrors() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "")
                        .param("lastName", "")
                        .param("email", "not-an-email")
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registration",
                        "firstName", "lastName", "email", "password"));

        verify(userService, never()).registerUser(any(User.class));
    }

    @Test
    void duplicateEmailShowsGlobalErrorOnForm() throws Exception {
        when(userService.registerUser(any(User.class)))
                .thenThrow(new ConflictException("Email is already registered"));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "Nora")
                        .param("lastName", "Test")
                        .param("email", "nora@example.com")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasErrors("registration"))
                .andExpect(content().string(containsString("already registered")));
    }

    @Test
    void registrationWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "Nora")
                        .param("lastName", "Test")
                        .param("email", "nora@example.com")
                        .param("password", "password123"))
                .andExpect(status().isForbidden());

        verify(userService, never()).registerUser(any(User.class));
    }

    // --- Browser-facing error states ---

    @Test
    void missingPagesRenderHtmlErrorViewForBrowsers() throws Exception {
        mockMvc.perform(get("/no-such-page")
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void missingPagesStillReturnProblemDetailForApiClients() throws Exception {
        mockMvc.perform(get("/no-such-page")
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }
}
