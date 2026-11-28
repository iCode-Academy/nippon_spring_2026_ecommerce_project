package mn.icode.service;

import mn.icode.dto.ProfileRequest;
import mn.icode.dto.ProfileResponse;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserProfileService {

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Get the currently authenticated user. */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    /** Get profile DTO for the current user. */
    public ProfileResponse getProfile() {
        User user = getCurrentUser();
        return new ProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone()
        );
    }

    /** Update profile for the current user. */
    @Transactional
    public ProfileResponse updateProfile(ProfileRequest request) {
        User user = getCurrentUser();
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);
        boolean emailChanged = email != null && !email.equals(user.getEmail());
        if (emailChanged && userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (emailChanged) {
                throw new ConflictException("Email is already registered");
            }
            throw exception;
        }
        return new ProfileResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone());
    }
}
