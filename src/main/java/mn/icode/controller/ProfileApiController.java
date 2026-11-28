package mn.icode.controller;

import mn.icode.dto.ProfileRequest;
import mn.icode.dto.ProfileResponse;
import mn.icode.service.UserProfileService;
import mn.icode.service.ProfileAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
public class ProfileApiController {

    private final UserProfileService userProfileService;
    private final ProfileAuthenticationService profileAuthenticationService;

    public ProfileApiController(UserProfileService userProfileService,
                                ProfileAuthenticationService profileAuthenticationService) {
        this.userProfileService = userProfileService;
        this.profileAuthenticationService = profileAuthenticationService;
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile() {
        ProfileResponse profile = userProfileService.getProfile();
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody ProfileRequest request,
                                                         Principal principal,
                                                         HttpServletRequest servletRequest,
                                                         HttpServletResponse servletResponse) {
        ProfileResponse updated = userProfileService.updateProfile(request);
        profileAuthenticationService.refreshAfterEmailChange(principal.getName(), updated.getEmail(),
                servletRequest, servletResponse);
        return ResponseEntity.ok(updated);
    }
}
