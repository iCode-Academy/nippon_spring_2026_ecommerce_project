package mn.icode.web;

import mn.icode.dto.ProfileRequest;
import mn.icode.dto.ProfileResponse;
import mn.icode.service.UserProfileService;
import mn.icode.service.AddressService;
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
    private final AddressService addressService;

    @Autowired
    public ProfileWebController(UserProfileService userProfileService, AddressService addressService) {
        this.userProfileService = userProfileService;
        this.addressService = addressService;
    }

    /** Show profile edit form. */
    @GetMapping
    public String getProfile(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        ProfileResponse profile = userProfileService.getProfile();
        model.addAttribute("profile", profile);
        model.addAttribute("profileForm", new ProfileRequest());
        return "profile";
    }

    /** Process profile update form. */
    @PostMapping
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileRequest form,
                                BindingResult result,
                                Model model,
                                RedirectAttributes redirectAttributes,
                                Principal principal) {

        if (principal == null) {
            return "redirect:/login";
        }

        if (result.hasErrors()) {
            // Re-render profile with validation errors
            ProfileResponse profile = userProfileService.getProfile();
            model.addAttribute("profile", profile);
            model.addAttribute("profileForm", form);
            return "profile";
        }

        try {
            userProfileService.updateProfile(form);
            redirectAttributes.addFlashAttribute("flashSuccess", "Profile updated successfully.");
            return "redirect:/profile";
        } catch (Exception e) {
            model.addAttribute("profile", userProfileService.getProfile());
            model.addAttribute("profileForm", form);
            model.addAttribute("flashError", "Error updating profile: " + e.getMessage());
            return "profile";
        }
    }
}