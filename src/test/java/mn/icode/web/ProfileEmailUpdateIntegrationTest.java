package mn.icode.web;

import mn.icode.entity.User;
import mn.icode.model.Role;
import mn.icode.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileEmailUpdateIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    private final List<Long> createdUserIds = new ArrayList<>();

    private User createUser(String email) {
        User customer = new User();
        customer.setEmail(email);
        customer.setFirstName("Nora");
        customer.setLastName("Test");
        customer.setPassword("encoded-password");
        customer.setRole(Role.CUSTOMER);
        customer = users.saveAndFlush(customer);
        createdUserIds.add(customer.getId());
        return customer;
    }

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(users::deleteById);
    }

    @Test
    void savedEmailAndAuthenticationWorkOnFollowingRequest() throws Exception {
        User customer = createUser("profile-old@example.com");

        MockHttpSession session = (MockHttpSession) mvc.perform(post("/profile")
                        .with(user(customer.getEmail()).roles("CUSTOMER")).with(csrf())
                        .param("firstName", "Updated").param("lastName", "Customer")
                        .param("email", "PROFILE-NEW@Example.com").param("phone", "99112233"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn().getRequest().getSession(false);

        assertFalse(users.findByEmail("profile-old@example.com").isPresent());
        assertTrue(users.findByEmail("profile-new@example.com").isPresent());
        mvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"profile-new@example.com\"")))
                .andExpect(content().string(containsString("value=\"Updated\"")));
    }

    @Test
    void duplicateEmailLeavesCurrentUserUnchanged() throws Exception {
        User customer = createUser("profile-original@example.com");
        createUser("profile-taken@example.com");

        mvc.perform(post("/profile").with(user(customer.getEmail()).roles("CUSTOMER"))
                        .with(csrf()).param("firstName", "Updated").param("lastName", "Customer")
                        .param("email", "PROFILE-TAKEN@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email is already registered")));

        assertTrue(users.findByEmail("profile-original@example.com").isPresent());
        assertTrue(users.findByEmail("profile-taken@example.com").isPresent());
    }
}
