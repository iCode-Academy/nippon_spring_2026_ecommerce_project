package mn.icode.web;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.AddressApiController;
import mn.icode.dto.AddressResponse;
import mn.icode.service.AddressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AddressApiController.class)
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class})
class AddressApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AddressService addresses;

    @Test
    void createRejectsMissingDistrictAndPhoneWithFieldErrors() throws Exception {
        mvc.perform(post("/api/addresses").with(csrf())
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Home","city":"Ulaanbaatar","addressLine":"Peace Ave 1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.district").value("District is required"))
                .andExpect(jsonPath("$.errors.phone").value("Phone is required"));
        verify(addresses, never()).createAddress(any());
    }

    @Test
    void updateRejectsBlankDistrictAndPhoneWithFieldErrors() throws Exception {
        mvc.perform(put("/api/addresses/7").with(csrf())
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Home","city":"Ulaanbaatar","district":"  ",
                                 "addressLine":"Peace Ave 1","phone":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.district").value("District is required"))
                .andExpect(jsonPath("$.errors.phone").value("Phone is required"));
        verify(addresses, never()).updateAddress(any(), any());
    }

    @Test
    void validAddressCanBeCreatedAndUpdated() throws Exception {
        AddressResponse address = new AddressResponse(7L, "Home", "Ulaanbaatar", "Sukhbaatar",
                "Peace Ave 1", "99112233", false);
        when(addresses.createAddress(any())).thenReturn(address);
        when(addresses.updateAddress(eq(7L), any())).thenReturn(address);
        String body = """
                {"title":"Home","city":"Ulaanbaatar","district":"Sukhbaatar",
                 "addressLine":"Peace Ave 1","phone":"99112233"}
                """;

        mvc.perform(post("/api/addresses").with(csrf())
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
        mvc.perform(put("/api/addresses/7").with(csrf())
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("99112233"));
        verify(addresses).createAddress(argThat(form ->
                "Sukhbaatar".equals(form.getDistrict()) && "99112233".equals(form.getPhone())));
        verify(addresses).updateAddress(eq(7L), argThat(form ->
                "Sukhbaatar".equals(form.getDistrict()) && "99112233".equals(form.getPhone())));
    }
}
