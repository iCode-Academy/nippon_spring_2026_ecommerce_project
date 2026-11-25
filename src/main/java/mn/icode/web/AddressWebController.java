package mn.icode.web;

import mn.icode.dto.AddressForm;
import mn.icode.dto.AddressResponse;
import mn.icode.service.AddressService;
import mn.icode.service.UserProfileService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/addresses")
public class AddressWebController {

    private final AddressService addressService;
    private final UserProfileService userProfileService;

    @Autowired
    public AddressWebController(AddressService addressService, UserProfileService userProfileService) {
        this.addressService = addressService;
        this.userProfileService = userProfileService;
    }

    /** List all addresses for the current user. */
    @GetMapping
    public String listAddresses(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        List<AddressResponse> addresses = addressService.listAddresses();
        AddressResponse defaultAddr = addressService.getDefaultAddress();

        model.addAttribute("addresses", addresses);
        model.addAttribute("defaultAddress", defaultAddr);
        model.addAttribute("addressForm", new AddressForm());
        model.addAttribute("addressToDelete", null);

        return "addresses";
    }

    /** Show create address form (only when no addresses exist). */
    @GetMapping("/create")
    public String showCreateForm(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        // Only show create form if no addresses exist; otherwise redirect
        List<AddressResponse> addresses = addressService.listAddresses();
        if (!addresses.isEmpty()) {
            return "redirect:/addresses";
        }

        model.addAttribute("addressForm", new AddressForm());
        return "addresses::form-content";
    }

    /** Process create address form. */
    @PostMapping("/create")
    public String createAddress(@Valid @ModelAttribute("addressForm") AddressForm form,
                                BindingResult result,
                                Model model,
                                RedirectAttributes redirectAttributes,
                                Principal principal) {

        if (principal == null) {
            return "redirect:/login";
        }

        if (result.hasErrors()) {
            model.addAttribute("addresses", addressService.listAddresses());
            model.addAttribute("defaultAddress", addressService.getDefaultAddress());
            return "addresses";
        }

        try {
            addressService.createAddress(form);
            redirectAttributes.addFlashAttribute("flashSuccess", "Address added successfully.");
            return "redirect:/addresses";
        } catch (Exception e) {
            model.addAttribute("addresses", addressService.listAddresses());
            model.addAttribute("defaultAddress", addressService.getDefaultAddress());
            model.addAttribute("flashError", "Error adding address: " + e.getMessage());
            return "addresses";
        }
    }

    /** Show edit address form. */
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        AddressResponse address = addressService.getDefaultAddress() != null
                && addressService.getDefaultAddress().getId().equals(id)
                ? addressService.getDefaultAddress()
                : null;

        // In a full implementation, we'd fetch the address by id with ownership check
        // For now, show the form with the address data
        model.addAttribute("addressForm", new AddressForm());
        model.addAttribute("addressId", id);
        model.addAttribute("isEdit", true);

        return "addresses::form-content";
    }

    /** Process edit address form. */
    @PostMapping("/edit/{id}")
    public String editAddress(@PathVariable Long id,
                              @Valid @ModelAttribute("addressForm") AddressForm form,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes,
                              Principal principal) {

        if (principal == null) {
            return "redirect:/login";
        }

        if (result.hasErrors()) {
            model.addAttribute("addressId", id);
            model.addAttribute("isEdit", true);
            return "addresses::form-content";
        }

        try {
            addressService.updateAddress(id, form);
            redirectAttributes.addFlashAttribute("flashSuccess", "Address updated successfully.");
            return "redirect:/addresses";
        } catch (AccessDeniedException e) {
            model.addAttribute("flashError", e.getMessage());
            model.addAttribute("addressId", id);
            model.addAttribute("isEdit", true);
            return "addresses";
        } catch (Exception e) {
            model.addAttribute("flashError", "Error updating address: " + e.getMessage());
            model.addAttribute("addressId", id);
            model.addAttribute("isEdit", true);
            return "addresses";
        }
    }

    /** Delete an address. */
    @GetMapping("/delete/{id}")
    public String deleteAddress(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        try {
            addressService.deleteAddress(id);
            redirectAttributes.addFlashAttribute("flashSuccess", "Address deleted successfully.");
        } catch (AccessDeniedException e) {
            model.addAttribute("flashError", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("flashError", "Error deleting address: " + e.getMessage());
        }

        return "redirect:/addresses";
    }
}