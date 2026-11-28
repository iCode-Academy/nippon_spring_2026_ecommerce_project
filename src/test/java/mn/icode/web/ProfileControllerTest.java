package mn.icode.web;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.ProfileApiController;
import mn.icode.dto.ProfileResponse;
import mn.icode.dto.ProfileRequest;
import mn.icode.exception.ConflictException;
import mn.icode.service.ProfileAuthenticationService;
import mn.icode.service.UserDetailsServiceImpl;
import mn.icode.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ProfileWebController.class, ProfileApiController.class})
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class, ProfileAuthenticationService.class})
class ProfileControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UserProfileService profiles;
    @MockitoBean UserDetailsServiceImpl userDetailsService;

    private ProfileResponse profile(String email) {
        return new ProfileResponse(7L, "Nora", "Test", email, "99112233");
    }

    private void newEmailCanAuthenticate() {
        when(userDetailsService.loadUserByUsername("new@example.com")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("new@example.com")
                        .password("encoded-password").roles("CUSTOMER").build());
    }

    @Test
    void profilePageBindsCurrentValuesAndPostsToCorrectAction() throws Exception {
        when(profiles.getProfile()).thenReturn(profile("old@example.com"));

        mvc.perform(get("/profile").with(user("old@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/profile\"")))
                .andExpect(content().string(containsString("name=\"firstName\" value=\"Nora\"")))
                .andExpect(content().string(containsString("name=\"email\" value=\"old@example.com\"")))
                .andExpect(content().string(containsString("href=\"/addresses\"")))
                .andExpect(content().string(containsString("aria-label=\"Account navigation\"")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void adminProfileOmitsCustomerAccountNavigation() throws Exception {
        when(profiles.getProfile()).thenReturn(profile("admin@example.com"));

        mvc.perform(get("/profile").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Account navigation"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Manage addresses"))));
    }

    @Test
    void invalidFormShowsErrorsAndKeepsSubmittedValues() throws Exception {
        mvc.perform(post("/profile").with(user("old@example.com").roles("CUSTOMER"))
                        .with(csrf()).param("firstName", "Nora").param("lastName", "Test")
                        .param("email", "not-an-email").param("phone", "99112233"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email must be valid")))
                .andExpect(content().string(containsString("value=\"not-an-email\"")));
        verify(profiles, never()).updateProfile(any());
    }

    @Test
    void duplicateEmailShowsFieldErrorWithoutChangingSession() throws Exception {
        when(profiles.updateProfile(any(ProfileRequest.class)))
                .thenThrow(new ConflictException("Email is already registered"));

        mvc.perform(post("/profile").with(user("old@example.com").roles("CUSTOMER"))
                        .with(csrf()).param("firstName", "Nora").param("lastName", "Test")
                        .param("email", "taken@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email is already registered")))
                .andExpect(content().string(containsString("value=\"taken@example.com\"")));
        verify(userDetailsService, never()).loadUserByUsername("taken@example.com");
    }

    @Test
    void overlongPhoneShowsInlineValidationError() throws Exception {
        mvc.perform(post("/profile").with(user("old@example.com").roles("CUSTOMER"))
                        .with(csrf()).param("firstName", "Nora").param("lastName", "Test")
                        .param("email", "old@example.com").param("phone", "1".repeat(21)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phone must not exceed 20 characters")))
                .andExpect(content().string(containsString("value=\"" + "1".repeat(21) + "\"")));
        verify(profiles, never()).updateProfile(any());
    }

    @Test
    void webEmailUpdatePersistsNewAuthenticationForNextRequest() throws Exception {
        when(profiles.updateProfile(any(ProfileRequest.class))).thenReturn(profile("new@example.com"));
        when(profiles.getProfile()).thenAnswer(invocation ->
                profile(SecurityContextHolder.getContext().getAuthentication().getName()));
        newEmailCanAuthenticate();

        MockHttpSession session = (MockHttpSession) mvc.perform(post("/profile")
                        .with(user("old@example.com").roles("CUSTOMER")).with(csrf())
                        .param("firstName", "Nora").param("lastName", "Test")
                        .param("email", "new@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"new@example.com\"")))
                .andExpect(content().string(containsString("new@example.com")));
    }

    @Test
    void apiEmailUpdatePersistsNewAuthenticationForNextRequest() throws Exception {
        when(profiles.updateProfile(any(ProfileRequest.class))).thenReturn(profile("new@example.com"));
        when(profiles.getProfile()).thenAnswer(invocation ->
                profile(SecurityContextHolder.getContext().getAuthentication().getName()));
        newEmailCanAuthenticate();

        MockHttpSession session = (MockHttpSession) mvc.perform(put("/api/profile")
                        .with(user("old@example.com").roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Nora","lastName":"Test","email":"new@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    void apiDuplicateEmailReturnsConflictAndInvalidEmailReturnsValidationErrors() throws Exception {
        when(profiles.updateProfile(any(ProfileRequest.class)))
                .thenThrow(new ConflictException("Email is already registered"));

        mvc.perform(put("/api/profile").with(user("old@example.com").roles("CUSTOMER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Nora","lastName":"Test","email":"taken@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));

        mvc.perform(put("/api/profile").with(user("old@example.com").roles("CUSTOMER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Nora","lastName":"Test","email":"invalid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email must be valid"));
    }
}
