package mn.icode.controller;

import mn.icode.dto.ProfileRequest;
import mn.icode.dto.ProfileResponse;
import mn.icode.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
public class ProfileApiController {

    private final UserProfileService userProfileService;

    public ProfileApiController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile() {
        ProfileResponse profile = userProfileService.getProfile();
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody ProfileRequest request) {
        ProfileResponse updated = userProfileService.updateProfile(request);
        return ResponseEntity.ok(updated);
    }
}