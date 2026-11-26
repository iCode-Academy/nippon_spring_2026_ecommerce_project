package mn.icode.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.CheckoutController;
import mn.icode.dto.CartItemResponse;
import mn.icode.dto.CartResponse;
import mn.icode.dto.CheckoutResponse;
import mn.icode.entity.User;
import mn.icode.model.Role;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;
import mn.icode.service.AddressService;
import mn.icode.service.CartService;
import mn.icode.service.CheckoutValidationService;

@WebMvcTest(controllers = CheckoutController.class)
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class})
class CheckoutWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CheckoutValidationService validationService;

    @MockitoBean
    private AddressService addressService;

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

    private static CartItemResponse sampleItem(int quantity, BigDecimal price) {
        return new CartItemResponse(
                1L,
                1L,
                "Test Product",
                quantity,
                price,
                true,
                price.multiply(BigDecimal.valueOf(quantity)));
    }

    @Test
    void checkoutPageRequiresAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/checkout"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void checkoutPageShowsFormForCustomer() throws Exception {
        CartItemResponse item = sampleItem(1, new BigDecimal("25.00"));
        when(cartService.getCart("nora@example.com"))
                .thenReturn(new CartResponse(1L, List.of(item), new BigDecimal("25.00")));
        when(userRepository.findByEmail("nora@example.com"))
                .thenReturn(Optional.of(customer()));

        mockMvc.perform(MockMvcRequestBuilders.get("/checkout")
                        .with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("checkout"))
                .andExpect(MockMvcResultMatchers.content().string(containsString("Checkout")));
    }

    @Test
    void checkoutPageShowsEmptyCartState() throws Exception {
        when(cartService.getCart("nora@example.com"))
                .thenReturn(new CartResponse(null, List.of(), BigDecimal.ZERO));
        when(userRepository.findByEmail("nora@example.com"))
                .thenReturn(Optional.of(customer()));

        mockMvc.perform(MockMvcRequestBuilders.get("/checkout")
                        .with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void checkoutProcessesWithForeignAddress_returnsError() throws Exception {
        CartItemResponse item = sampleItem(1, new BigDecimal("25.00"));
        CartResponse cartResponse = new CartResponse(1L, List.of(item), new BigDecimal("25.00"));

        when(cartService.getCart("nora@example.com")).thenReturn(cartResponse);
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer()));
        when(addressService.listAddresses()).thenReturn(List.of());
        when(validationService.validateCheckout(99L))
                .thenReturn(new CheckoutResponse(
                        false,
                        List.of("Selected delivery address does not belong to your account"),
                        cartResponse));

        mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                        .with(csrf())
                        .param("deliveryAddressId", "99")
                        .with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void checkoutProcessesWithInsufficientStock_returnsError() throws Exception {
        CartItemResponse item = sampleItem(5, new BigDecimal("5.00"));
        CartResponse cartResponse = new CartResponse(1L, List.of(item), new BigDecimal("25.00"));

        when(cartService.getCart("nora@example.com")).thenReturn(cartResponse);
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer()));
        when(validationService.validateCheckout(any(Long.class)))
                .thenReturn(new CheckoutResponse(
                        false,
                        List.of("Insufficient stock for product 'Test Product': requested 5 but only 3 available"),
                        cartResponse));

        mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                        .with(csrf())
                        .param("deliveryAddressId", "1")
                        .with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void checkoutProcessesSuccessfully_redirects() throws Exception {
        CartItemResponse item = sampleItem(1, new BigDecimal("12.50"));
        CartResponse cartResponse = new CartResponse(1L, List.of(item), new BigDecimal("12.50"));

        when(cartService.getCart("nora@example.com")).thenReturn(cartResponse);
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer()));
        when(validationService.validateCheckout(any(Long.class)))
                .thenReturn(new CheckoutResponse(true, List.of(), cartResponse));

        mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                        .with(csrf())
                        .param("deliveryAddressId", "1")
                        .with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().is3xxRedirection());
    }
}