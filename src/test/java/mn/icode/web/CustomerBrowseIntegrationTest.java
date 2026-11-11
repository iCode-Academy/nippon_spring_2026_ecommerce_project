package mn.icode.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.transaction.Transactional;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerBrowseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private ProductRepository productRepository;

        private Category category;

        @BeforeEach
        void setUp() {
                category = categoryRepository.save(new Category("Browse " + UUID.randomUUID()));

                saveProduct("Silver PEN", "25.00", 5, true, LocalDateTime.now().minusDays(5));
                saveProduct("Golden Pen", "60.00", 0, true, LocalDateTime.now().minusDays(4));
                saveProduct("Cheap pencil", "1.50", 100, true, LocalDateTime.now().minusDays(3));
                saveProduct("Hidden Draft Pen", "10.00", 7, false, LocalDateTime.now().minusDays(2));

                for (int i = 1; i <= 9; i++) {
                        saveProduct("Filler Item " + i, "8.00", 4, true, LocalDateTime.now().minusDays(1));
                }
                saveProduct("Last Filler Item", "8.00", 4, true, LocalDateTime.now().minusDays(1));
        }

        private Product saveProduct(String name, String price, int stock, boolean active,
                        LocalDateTime createdAt) {
                Product product = new Product(category, name, "Test product",
                                new BigDecimal(price), stock, active);
                ReflectionTestUtils.setField(product, "createdAt", createdAt);
                return productRepository.save(product);
        }

        private String browsePageAsHtml(String... params) throws Exception {
                MockHttpServletRequestBuilder request = get("/products");
                for (int i = 0; i < params.length; i += 2) {
                        request = request.param(params[i], params[i + 1]);
                }
                return mockMvc.perform(request.with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
        }

        // --- Browse: only active products are listed ---

        @Test
        void browsePageListsOnlyActiveProducts() throws Exception {
                mockMvc.perform(get("/products").with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/list"))
                                .andExpect(content().string(containsString("Silver PEN")))
                                .andExpect(content().string(containsString("Golden Pen")))
                                .andExpect(content().string(not(containsString("Hidden Draft Pen"))));
        }

        // --- Search by keyword (case-insensitive, active products only) ---

        @Test
        void browsePageFiltersProductsByKeyword() throws Exception {
                mockMvc.perform(get("/products")
                                .param("keyword", "PEN")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Silver PEN")))
                                .andExpect(content().string(containsString("Golden Pen")))
                                .andExpect(content().string(containsString("Cheap pencil")))
                                .andExpect(content().string(not(containsString("Filler Item 1"))))
                                .andExpect(content().string(not(containsString("Hidden Draft Pen"))));
        }

        // --- Filter by category ---

        @Test
        void browsePageFiltersProductsByCategory() throws Exception {
                Category other = categoryRepository.save(new Category("Other " + UUID.randomUUID()));
                productRepository.save(new Product(other, "Other Category Sticker",
                                "Test product", new BigDecimal("2.00"), 9, true));

                mockMvc.perform(get("/products")
                                .param("categoryId", category.getId().toString())
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Silver PEN")))
                                .andExpect(content().string(not(containsString("Other Category Sticker"))));
        }

        // --- Filter by price range ---

        @Test
        void browsePageFiltersProductsByPriceRange() throws Exception {
                mockMvc.perform(get("/products")
                                .param("keyword", "pen")
                                .param("minPrice", "10.00")
                                .param("maxPrice", "30.00")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Silver PEN")))
                                .andExpect(content().string(not(containsString("Golden Pen"))))
                                .andExpect(content().string(not(containsString("Cheap pencil"))));
        }

        // --- Filter by availability ---

        @Test
        void browsePageFiltersInStockProductsOnly() throws Exception {
                mockMvc.perform(get("/products")
                                .param("keyword", "pen")
                                .param("inStock", "true")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Silver PEN")))
                                .andExpect(content().string(containsString("Cheap pencil")))
                                .andExpect(content().string(not(containsString("Golden Pen"))));
        }

        @Test
        void browsePageFiltersOutOfStockProductsOnly() throws Exception {
                mockMvc.perform(get("/products")
                                .param("keyword", "pen")
                                .param("inStock", "false")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Golden Pen")))
                                .andExpect(content().string(not(containsString("Silver PEN"))))
                                .andExpect(content().string(not(containsString("Cheap pencil"))));
        }

        // --- Sorting ---

        @Test
        void browsePageSortsProductsByPriceAscending() throws Exception {
                String html = browsePageAsHtml("keyword", "pen", "sort", "priceAsc");

                assertTrue(html.indexOf("Cheap pencil") < html.indexOf("Silver PEN"));
                assertTrue(html.indexOf("Silver PEN") < html.indexOf("Golden Pen"));
        }

        @Test
        void browsePageSortsProductsByPriceDescending() throws Exception {
                String html = browsePageAsHtml("keyword", "pen", "sort", "priceDesc");

                assertTrue(html.indexOf("Golden Pen") < html.indexOf("Silver PEN"));
                assertTrue(html.indexOf("Silver PEN") < html.indexOf("Cheap pencil"));
        }

        @Test
        void browsePageSortsNewestProductsFirst() throws Exception {
                String html = browsePageAsHtml("keyword", "pen", "sort", "newest");

                assertTrue(html.indexOf("Cheap pencil") < html.indexOf("Golden Pen"));
                assertTrue(html.indexOf("Golden Pen") < html.indexOf("Silver PEN"));
        }

        // --- Invalid filter values are handled gracefully ---

        @Test
        void browsePageShowsErrorMessageForInvalidPriceRange() throws Exception {
                mockMvc.perform(get("/products")
                                .param("minPrice", "50.00")
                                .param("maxPrice", "10.00")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/list"))
                                .andExpect(model().attribute("errorMessage",
                                                "Minimum price cannot be greater than maximum price"))
                                .andExpect(content().string(containsString(
                                                "Minimum price cannot be greater than maximum price")))
                                .andExpect(content().string(not(containsString("Silver PEN"))));
        }

        // --- Pagination (page size is fixed at 12) ---

        @Test
        void browsePagePaginatesProductsWithFixedPageSize() throws Exception {
                mockMvc.perform(get("/products")
                                .param("categoryId", category.getId().toString())
                                .param("page", "0")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(model().attribute("currentPage", 0))
                                .andExpect(model().attribute("totalPages", 2))
                                .andExpect(content().string(containsString("Page 1 of 2")))
                                .andExpect(content().string(not(containsString("Last Filler Item"))));

                mockMvc.perform(get("/products")
                                .param("categoryId", category.getId().toString())
                                .param("page", "1")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(model().attribute("currentPage", 1))
                                .andExpect(content().string(containsString("Page 2 of 2")))
                                .andExpect(content().string(containsString("Last Filler Item")))
                                .andExpect(content().string(not(containsString("Filler Item 1"))));
        }

        // --- REST API pagination (anonymous browsing) ---

        @Test
        void apiProductsReturnsFirstPageOfActiveProducts() throws Exception {
                mockMvc.perform(get("/api/products")
                                .param("size", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(5)))
                                .andExpect(jsonPath("$.totalElements").value(13))
                                .andExpect(jsonPath("$.totalPages").value(3))
                                .andExpect(jsonPath("$.number").value(0));
        }

        @Test
        void apiProductsReturnsLastPartialPage() throws Exception {
                mockMvc.perform(get("/api/products")
                                .param("page", "2")
                                .param("size", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(3)))
                                .andExpect(jsonPath("$.number").value(2));
        }

}
