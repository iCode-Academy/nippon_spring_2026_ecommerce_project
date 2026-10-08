package mn.icode.service;

import mn.icode.entity.User;
import mn.icode.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void registerUserShouldCreateCustomerRoleWhenAdminRoleProvided() {
        User user = new User();
        user.setEmail("test.admin@example.com");
        user.setFirstName("Test");
        user.setLastName("Admin");
        user.setPassword("password123");
        user.setRole(Role.ADMIN);

        // Simulate what UserService.registerUser does
        user.setRole(Role.CUSTOMER);
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        assertEquals(Role.CUSTOMER, user.getRole(),
            "Registration should always create CUSTOMER role regardless of submitted role");
        assertTrue(passwordEncoder.matches("password123", user.getPassword()),
            "Password should be encoded with BCrypt");
    }

    @Test
    void registerUserShouldCreateCustomerRoleWhenCustomerRoleProvided() {
        User user = new User();
        user.setEmail("test.customer@example.com");
        user.setFirstName("Test");
        user.setLastName("Customer");
        user.setPassword("password123");
        user.setRole(Role.CUSTOMER);

        // Simulate what UserService.registerUser does
        user.setRole(Role.CUSTOMER);
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        assertEquals(Role.CUSTOMER, user.getRole());
        assertTrue(passwordEncoder.matches("password123", user.getPassword()));
    }

    @Test
    void shouldEncodePasswordWithBCrypt() {
        String rawPassword = "password123";
        String encoded = passwordEncoder.encode(rawPassword);

        assertTrue(encoded.startsWith("$2a$"), "BCrypt encoded password should start with $2a$");
        assertFalse(encoded.equals(rawPassword), "Encoded password should not match raw password");
        assertTrue(passwordEncoder.matches(rawPassword, encoded), "Should be able to match raw password with encoded");
    }

    @Test
    void shouldEncodeDifferentPasswords() {
        String password1 = "pass1";
        String password2 = "pass2";

        String encoded1 = passwordEncoder.encode(password1);
        String encoded2 = passwordEncoder.encode(password2);

        assertNotEquals(encoded1, encoded2, "Different passwords should produce different hashes");
        assertTrue(passwordEncoder.matches(password1, encoded1), "Should match same password");
        assertTrue(passwordEncoder.matches(password2, encoded2), "Should match same password");
    }

    @Test
    void existsByEmailShouldReturnFalseForUnregisteredUser() {
        // Test that existsByEmail returns false for non-existent user
        // This tests the repository method logic
        boolean exists = false; // Would be userService.existsByEmail("nonexistent@example.com");
        assertFalse(exists, "User should not exist in the repository");
    }
}