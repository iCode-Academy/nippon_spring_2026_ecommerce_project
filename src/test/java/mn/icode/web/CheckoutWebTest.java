package mn.icode.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.CheckoutController;
import mn.icode.dto.AddressResponse;
import mn.icode.dto.CartItemResponse;
import mn.icode.dto.CartResponse;
import mn.icode.dto.CheckoutResponse;
import mn.icode.entity.Order;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.Role;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.UserRepository;
import mn.icode.service.AddressService;
import mn.icode.service.CartService;
import mn.icode.service.CheckoutService;
import mn.icode.service.CheckoutValidationService;

@WebMvcTest(controllers = CheckoutController.class)
@Import({ SecurityConfig.class, ServletWebSecurityAutoConfiguration.class })
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

        @MockitoBean
        private CheckoutService checkoutService;

        private void withCartAndAddress() {
                CartItemResponse item = sampleItem(1, new BigDecimal("12.50"));
                when(cartService.getCart("nora@example.com"))
                                .thenReturn(new CartResponse(1L, List.of(item), new BigDecimal("12.50")));
                when(addressService.listAddresses()).thenReturn(List.of(new AddressResponse(
                                1L, "Home", "Ulaanbaatar", "Sukhbaatar", "Peace Ave 1", "99112233", true)));
        }

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
                when(addressService.listAddresses()).thenReturn(List.of(new AddressResponse(
                                1L, "Home", "Ulaanbaatar", "Sukhbaatar", "Peace Ave 1", "99112233", true)));

                mockMvc.perform(MockMvcRequestBuilders.get("/checkout")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(MockMvcResultMatchers.view().name("checkout"))
                                .andExpect(MockMvcResultMatchers.content().string(containsString("Place order")))
                                .andExpect(MockMvcResultMatchers.content()
                                                .string(containsString("name=\"deliveryAddressId\"")));
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
                                .andExpect(status().isOk())
                                .andExpect(MockMvcResultMatchers.content().string(containsString(
                                                "Selected delivery address does not belong to your account")));
                verifyNoInteractions(checkoutService);
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
                                .andExpect(status().isOk())
                                .andExpect(MockMvcResultMatchers.content()
                                                .string(containsString("Insufficient stock")));
                verifyNoInteractions(checkoutService);
        }

        @Test
        void checkoutProcessesSuccessfully_redirects() throws Exception {
                CartItemResponse item = sampleItem(1, new BigDecimal("12.50"));
                CartResponse cartResponse = new CartResponse(1L, List.of(item), new BigDecimal("12.50"));

                when(cartService.getCart("nora@example.com")).thenReturn(cartResponse);
                when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer()));
                when(validationService.validateCheckout(any(Long.class)))
                                .thenReturn(new CheckoutResponse(true, List.of(), cartResponse));
                Order order = mock(Order.class);
                when(order.getId()).thenReturn(7L);
                when(checkoutService.checkout("nora@example.com", 1L)).thenReturn(order);

                mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                                .with(csrf())
                                .param("deliveryAddressId", "1")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/orders/7"));
                verify(checkoutService).checkout("nora@example.com", 1L);
        }

        @Test
        void checkoutMissingAddressRendersValidationErrorAndRepresentsForm() throws Exception {
                withCartAndAddress();
                mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                                .with(csrf()).with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(MockMvcResultMatchers.content()
                                                .string(containsString("Delivery address is required")))
                                .andExpect(MockMvcResultMatchers.content().string(containsString("Home, Ulaanbaatar")));
                verifyNoInteractions(checkoutService);
        }

        @Test
        void preflightFailureRendersMessageAndDoesNotCreateOrder() throws Exception {
                withCartAndAddress();
                when(validationService.validateCheckout(1L))
                                .thenReturn(new CheckoutResponse(false, List.of("Cart is empty"), null));
                mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                                .with(csrf()).with(user("nora@example.com").roles("CUSTOMER"))
                                .param("deliveryAddressId", "1"))
                                .andExpect(status().isOk())
                                .andExpect(MockMvcResultMatchers.content().string(containsString("Cart is empty")));
                verifyNoInteractions(checkoutService);
        }

        @Test
        void stockChangeAfterPreflightRendersErrorWithoutRedirect() throws Exception {
                withCartAndAddress();
                when(validationService.validateCheckout(1L))
                                .thenReturn(new CheckoutResponse(true, List.of(), null));
                when(checkoutService.checkout("nora@example.com", 1L))
                                .thenThrow(new ConflictException("Insufficient stock"));
                mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                                .with(csrf()).with(user("nora@example.com").roles("CUSTOMER"))
                                .param("deliveryAddressId", "1"))
                                .andExpect(status().isOk())
                                .andExpect(MockMvcResultMatchers.content().string(containsString("Insufficient stock")))
                                .andExpect(MockMvcResultMatchers.content().string(containsString("Home, Ulaanbaatar")));
        }

        @Test
        void addressRemovedAfterPreflightRendersError() throws Exception {
                withCartAndAddress();
                when(validationService.validateCheckout(1L))
                                .thenReturn(new CheckoutResponse(true, List.of(), null));
                when(checkoutService.checkout("nora@example.com", 1L))
                                .thenThrow(new ResourceNotFoundException("Delivery address not found"));
                mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                                .with(csrf()).with(user("nora@example.com").roles("CUSTOMER"))
                                .param("deliveryAddressId", "1"))
                                .andExpect(status().isOk())
                                .andExpect(MockMvcResultMatchers.content()
                                                .string(containsString("Delivery address not found")));
        }

        @Test
        void noAddressesShowsHelpfulEmptyState() throws Exception {
                when(cartService.getCart("nora@example.com")).thenReturn(new CartResponse(1L,
                                List.of(sampleItem(1, new BigDecimal("12.50"))), new BigDecimal("12.50")));
                when(addressService.listAddresses()).thenReturn(List.of());
                mockMvc.perform(MockMvcRequestBuilders.get("/checkout")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(MockMvcResultMatchers.content().string(containsString("Add an address")))
                                .andExpect(MockMvcResultMatchers.content().string(not(containsString("Place order"))));
        }

        @Test
        void adminAndMissingCsrfCannotPlaceOrder() throws Exception {
                mockMvc.perform(MockMvcRequestBuilders.get("/checkout")
                                .with(user("admin@example.com").roles("ADMIN")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                                .with(csrf()).with(user("admin@example.com").roles("ADMIN"))
                                .param("deliveryAddressId", "1"))
                                .andExpect(status().isForbidden());
                mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .param("deliveryAddressId", "1"))
                                .andExpect(status().isForbidden());
                verifyNoInteractions(checkoutService);
        }

        @Test
        void checkout_ignoresClientSuppliedPricesAndTotals() throws Exception {

                CartItemResponse item = sampleItem(
                                2, new BigDecimal("12.50"));

                CartResponse cartResponse = new CartResponse(
                                1L, List.of(item), new BigDecimal("25.00"));

                when(cartService.getCart("nora@example.com"))
                                .thenReturn(cartResponse);

                when(userRepository.findByEmail("nora@example.com"))
                                .thenReturn(Optional.of(customer()));

                when(validationService.validateCheckout(1L))
                                .thenReturn(new CheckoutResponse(
                                                true, List.of(), cartResponse));

                Order order = mock(Order.class);

                when(order.getId()).thenReturn(7L);

                when(checkoutService.checkout("nora@example.com", 1L))
                                .thenReturn(order);

                mockMvc.perform(MockMvcRequestBuilders.post("/checkout")
                                .with(csrf())
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .param("deliveryAddressId", "1")
                                .param("price", "0.01")
                                .param("unitPrice", "0.01")
                                .param("subtotal", "0.01")
                                .param("total", "0.01")
                                .param("quantity", "100")
                                .param("userId", "999"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/orders/7"));

                verify(validationService).validateCheckout(1L);

                verify(checkoutService).checkout("nora@example.com", 1L);
        }
}
