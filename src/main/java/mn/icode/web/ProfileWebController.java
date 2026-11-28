package mn.icode.web;

import mn.icode.dto.ProfileRequest;
import mn.icode.dto.ProfileResponse;
import mn.icode.service.UserProfileService;
import mn.icode.service.ProfileAuthenticationService;
import mn.icode.exception.ConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/profile")
public class ProfileWebController {

    private final UserProfileService userProfileService;
    private final ProfileAuthenticationService profileAuthenticationService;

    @Autowired
    public ProfileWebController(UserProfileService userProfileService,
                                ProfileAuthenticationService profileAuthenticationService) {
        this.userProfileService = userProfileService;
        this.profileAuthenticationService = profileAuthenticationService;
    }

    /** Show profile edit form. */
    @GetMapping
    public String getProfile(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        ProfileResponse profile = userProfileService.getProfile();
        model.addAttribute("profile", profile);
        ProfileRequest form = new ProfileRequest();
        form.setFirstName(profile.getFirstName());
        form.setLastName(profile.getLastName());
        form.setEmail(profile.getEmail());
        form.setPhone(profile.getPhone());
        model.addAttribute("profileForm", form);
        return "profile";
    }

    /** Process profile update form. */
    @PostMapping
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileRequest form,
                                BindingResult result,
                                Model model,
                                RedirectAttributes redirectAttributes,
                                Principal principal,
                                HttpServletRequest request,
                                HttpServletResponse response) {

        if (principal == null) {
            return "redirect:/login";
        }

        if (result.hasErrors()) {
            return "profile";
        }

        try {
            ProfileResponse updated = userProfileService.updateProfile(form);
            profileAuthenticationService.refreshAfterEmailChange(principal.getName(), updated.getEmail(),
                    request, response);
            redirectAttributes.addFlashAttribute("flashSuccess", "Profile updated successfully.");
            return "redirect:/profile";
        } catch (ConflictException e) {
            result.rejectValue("email", "duplicate", e.getMessage());
            return "profile";
        }
    }
}
