package mn.icode.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import mn.icode.config.SecurityConfig;
import mn.icode.dto.AddressResponse;
import mn.icode.service.AddressService;
import mn.icode.service.UserProfileService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AddressWebController.class)
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class})
class AddressWebControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AddressService addresses;
    @MockitoBean UserProfileService profiles;

    @Test
    void emptyAddressPageRendersWorkingCreateForm() throws Exception {
        when(addresses.listAddresses()).thenReturn(List.of());
        mvc.perform(get("/addresses").with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(view().name("addresses"))
                .andExpect(content().string(containsString("No addresses yet")))
                .andExpect(content().string(containsString("action=\"/addresses/create\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void createAddressAndInvalidFormAreHandled() throws Exception {
        when(addresses.listAddresses()).thenReturn(List.of());
        mvc.perform(post("/addresses/create").with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf()).param("title", "Home").param("city", "Ulaanbaatar")
                        .param("district", "Sukhbaatar").param("addressLine", "Peace Ave 1")
                        .param("phone", "99112233"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/addresses"));
        verify(addresses).createAddress(any());

        mvc.perform(post("/addresses/create").with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf()).param("title", "Home"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("City is required")));
    }

    @Test
    void missingDistrictAndPhoneCannotBeSavedOnCreateOrEdit() throws Exception {
        mvc.perform(post("/addresses/create").with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf()).param("title", "Home").param("city", "Ulaanbaatar")
                        .param("district", " ").param("addressLine", "Peace Ave 1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("District is required")))
                .andExpect(content().string(containsString("Phone is required")));
        verify(addresses, never()).createAddress(any());

        mvc.perform(post("/addresses/edit/7").with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf()).param("title", "Home").param("city", "Ulaanbaatar")
                        .param("district", "Sukhbaatar").param("addressLine", "Peace Ave 1")
                        .param("phone", "  "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phone is required")));
        verify(addresses, never()).updateAddress(any(), any());
    }

    @Test
    void completeAddressCanBeUpdated() throws Exception {
        mvc.perform(post("/addresses/edit/7").with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf()).param("title", "Home").param("city", "Ulaanbaatar")
                        .param("district", "Sukhbaatar").param("addressLine", "Peace Ave 1")
                        .param("phone", "99112233"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/addresses"));
        verify(addresses).updateAddress(any(), any());
    }

    @Test
    void existingAddressCanBeEditedAndDeletedWithCsrf() throws Exception {
        var address = new AddressResponse(7L, "Home", "Ulaanbaatar", "Sukhbaatar",
                "Peace Ave 1", "99112233", true);
        when(addresses.listAddresses()).thenReturn(List.of(address));
        when(addresses.getDefaultAddress()).thenReturn(address);
        mvc.perform(get("/addresses/edit/7").with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/addresses/edit/7\"")))
                .andExpect(content().string(containsString("value=\"Peace Ave 1\"")));
        mvc.perform(post("/addresses/delete/7").with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/addresses"));
        verify(addresses).deleteAddress(7L);
    }
}
