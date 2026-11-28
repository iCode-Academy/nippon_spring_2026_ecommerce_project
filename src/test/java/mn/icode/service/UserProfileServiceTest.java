package mn.icode.service;

import mn.icode.dto.ProfileRequest;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserProfileService profiles = new UserProfileService(users);
    private final User currentUser = new User();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("old@example.com", "password"));
        currentUser.setId(7L);
        currentUser.setFirstName("Nora");
        currentUser.setLastName("Test");
        currentUser.setEmail("old@example.com");
        when(users.findByEmail("old@example.com")).thenReturn(Optional.of(currentUser));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private ProfileRequest form(String email) {
        ProfileRequest form = new ProfileRequest();
        form.setFirstName("Updated");
        form.setLastName("Customer");
        form.setEmail(email);
        form.setPhone("99112233");
        return form;
    }

    @Test
    void updatesDetailsAndReturnsSavedProfileWithoutLookingUpOldEmailAgain() {
        when(users.saveAndFlush(currentUser)).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = profiles.updateProfile(form(" NEW@Example.com "));

        assertEquals("new@example.com", updated.getEmail());
        assertEquals("Updated", updated.getFirstName());
        assertEquals("Customer", updated.getLastName());
        assertEquals("99112233", updated.getPhone());
        verify(users, times(1)).findByEmail("old@example.com");
        verify(users).existsByEmail("new@example.com");
    }

    @Test
    void updatingOwnEmailWithDifferentCaseDoesNotConflict() {
        when(users.saveAndFlush(currentUser)).thenReturn(currentUser);

        assertEquals("old@example.com", profiles.updateProfile(form(" OLD@example.com ")).getEmail());

        verify(users, never()).existsByEmail(any());
    }

    @Test
    void duplicateEmailDoesNotChangeExistingUser() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> profiles.updateProfile(form(" Taken@Example.com ")));

        assertEquals("old@example.com", currentUser.getEmail());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateAtDatabaseConstraintIsReportedAsConflict() {
        when(users.saveAndFlush(currentUser)).thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertEquals("Email is already registered", assertThrows(ConflictException.class,
                () -> profiles.updateProfile(form("taken@example.com"))).getMessage());
    }
}
