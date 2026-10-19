package mn.icode.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.AdminCategoryApiController;
import mn.icode.controller.AdminCategoryController;
import mn.icode.entity.Category;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.service.CategoryService;

@WebMvcTest(controllers = {AdminCategoryController.class, AdminCategoryApiController.class})
@Import({SecurityConfig.class, CategoryService.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class AdminCategoryPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private ProductRepository productRepository;

    private static Category category(Long id, String name) {
        Category category = new Category(name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    // --- Page shell: JavaScript client, CSRF via meta tags only ---

    @Test
    void adminPageRendersJavaScriptShell() throws Exception {
        mockMvc.perform(get("/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories"))
                .andExpect(content().string(containsString("id=\"formSection\"")))
                .andExpect(content().string(containsString("id=\"categoryForm\"")))
                .andExpect(content().string(containsString("id=\"editId\"")))
                .andExpect(content().string(containsString("id=\"fName\"")))
                .andExpect(content().string(containsString("id=\"categoriesTableBody\"")))
                .andExpect(content().string(containsString("id=\"emptyTable\"")))
                .andExpect(content().string(containsString("id=\"newCategoryBtn\"")))
                .andExpect(content().string(containsString("js/admin-categories.js")))
                .andExpect(content().string(containsString("No categories yet")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("/admin/categories/new"))));
    }

    @Test
    void categoryFormCarriesNoServerInjectedCsrfToken() throws Exception {
        String page = mockMvc.perform(get("/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(page)
                .contains("<meta name=\"_csrf\"")
                .contains("<meta name=\"_csrf_header\"");

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<form[^>]*id=\"categoryForm\"[^>]*>.*?</form>",
                        java.util.regex.Pattern.DOTALL)
                .matcher(page);
        org.assertj.core.api.Assertions.assertThat(matcher.find())
                .as("category form is rendered")
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(matcher.group())
                .doesNotContain("_csrf");
    }

    // --- Authorization: anonymous redirected, CUSTOMER denied, on page and API ---

    @Test
    void anonymousUsersAreSentToLoginForCategoryRoutes() throws Exception {
        mockMvc.perform(get("/admin/categories"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void customersCannotViewTheCategoryPage() throws Exception {
        mockMvc.perform(get("/admin/categories")
                        .with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void customersCannotUseTheCategoryApi() throws Exception {
        mockMvc.perform(get("/api/admin/categories")
                        .with(user("nora@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/categories")
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stationery\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/categories/1")
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stationery\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/categories/1")
                        .with(user("nora@example.com").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(categoryRepository, never()).save(any(Category.class));
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    // --- API: list ---

    @Test
    void adminCanListCategoriesAsJson() throws Exception {
        when(categoryRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(category(1L, "Books"), category(2L, "Stationery")));

        mockMvc.perform(get("/api/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Books"))
                .andExpect(jsonPath("$[1].name").value("Stationery"));
    }

    @Test
    void emptyCategoryListReturnsEmptyJsonArray() throws Exception {
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- API: create ---

    @Test
    void creatingCategoryReturns201WithBody() throws Exception {
        when(categoryRepository.existsByNameIgnoreCase("Stationery")).thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stationery\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Stationery"));

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void blankCategoryNameIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists());

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void overlongCategoryNameIsRejectedWith400() throws Exception {
        String longName = "a".repeat(151);

        mockMvc.perform(post("/api/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void duplicateCategoryNameIsRejectedWith409() throws Exception {
        when(categoryRepository.existsByNameIgnoreCase("Stationery")).thenReturn(true);

        mockMvc.perform(post("/api/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stationery\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already exists")));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stationery\"}"))
                .andExpect(status().isForbidden());

        verify(categoryRepository, never()).save(any(Category.class));
    }

    // --- API: update ---

    @Test
    void updatingCategoryReturns200WithBody() throws Exception {
        Category own = category(5L, "Stationery");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(own));
        when(categoryRepository.findByNameIgnoreCase("Books")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/admin/categories/5")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Books\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Books"));

        verify(categoryRepository).save(own);
    }

    @Test
    void updateToDuplicateNameIsRejectedWith409() throws Exception {
        when(categoryRepository.findById(5L))
                .thenReturn(Optional.of(category(5L, "Books")));
        when(categoryRepository.findByNameIgnoreCase("Stationery"))
                .thenReturn(Optional.of(category(9L, "Stationery")));

        mockMvc.perform(put("/api/admin/categories/5")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stationery\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already exists")));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updatingMissingCategoryReturns404() throws Exception {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/admin/categories/99")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Books\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(put("/api/admin/categories/5")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Books\"}"))
                .andExpect(status().isForbidden());

        verify(categoryRepository, never()).save(any(Category.class));
    }

    // --- API: safe delete ---

    @Test
    void deletingUnreferencedCategoryReturns204() throws Exception {
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category(1L, "Extras")));
        when(productRepository.countByCategoryId(1L)).thenReturn(0L);

        mockMvc.perform(delete("/api/admin/categories/1")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(categoryRepository).delete(any(Category.class));
    }

    @Test
    void referencedCategoryDeleteIsRefusedWith409() throws Exception {
        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(category(2L, "Stationery")));
        when(productRepository.countByCategoryId(2L)).thenReturn(4L);

        mockMvc.perform(delete("/api/admin/categories/2")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Cannot delete")))
                .andExpect(jsonPath("$.detail", containsString("Stationery")))
                .andExpect(jsonPath("$.detail", containsString("4 product(s)")));

        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deletingMissingCategoryReturns404() throws Exception {
        when(categoryRepository.findById(77L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/admin/categories/77")
                        .with(user("boss@example.com").roles("ADMIN"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(delete("/api/admin/categories/1")
                        .with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(categoryRepository, never()).delete(any(Category.class));
    }
}
