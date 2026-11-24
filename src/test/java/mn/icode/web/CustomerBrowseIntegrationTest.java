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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.transaction.Transactional;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.entity.ProductImage;
import mn.icode.entity.Review;
import mn.icode.entity.User;
import mn.icode.model.Role;
import mn.icode.model.ReviewStatus;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductImageRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.ReviewRepository;
import mn.icode.repository.UserRepository;

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

        @Autowired
        private ProductImageRepository productImageRepository;

        @Autowired
        private ReviewRepository reviewRepository;

        @Autowired
        private UserRepository userRepository;

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

        private User saveCustomer(String email) {
                User user = new User();
                user.setFirstName("Test");
                user.setLastName("Customer");
                user.setEmail(email);
                user.setPassword("secret");
                user.setRole(Role.CUSTOMER);
                return userRepository.save(user);
        }

        private void saveApprovedReview(User customer, Product product, int rating) {
                Review review = new Review(customer, product, rating, "Published review");
                ReflectionTestUtils.setField(review, "moderationStatus", ReviewStatus.APPROVED);
                reviewRepository.save(review);
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

        @Test
        void browsePageShowsThumbnailResultCountAndFilterChips() throws Exception {
                Product product = productRepository.findAll().stream()
                                .filter(candidate -> "Silver PEN".equals(candidate.getName()))
                                .findFirst()
                                .orElseThrow();
                ProductImage image = productImageRepository.save(
                                new ProductImage(product, "https://example.com/silver-pen.jpg", 0));
                product.getImages().add(image);

                mockMvc.perform(get("/products")
                                .param("keyword", "Silver")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString(
                                                "src=\"https://example.com/silver-pen.jpg\"")))
                                .andExpect(content().string(containsString("View details")))
                                .andExpect(content().string(containsString("1 product found")))
                                .andExpect(content().string(containsString("Search: Silver")))
                                .andExpect(content().string(containsString("Clear all")));
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
                                .andExpect(content().string(containsString("aria-label=\"Go to page 2\"")))
                                .andExpect(content().string(containsString("aria-current=\"page\"")))
                                .andExpect(content().string(not(containsString("Last Filler Item"))));

                mockMvc.perform(get("/products")
                                .param("categoryId", category.getId().toString())
                                .param("page", "1")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(model().attribute("currentPage", 1))
                                .andExpect(content().string(containsString("aria-label=\"Go to page 1\"")))
                                .andExpect(content().string(containsString("aria-current=\"page\"")))
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

        @Test
        void browsePageLinksProductsToTheirDetailPage() throws Exception {
                Product product = productRepository.findAll().stream()
                                .filter(candidate -> "Silver PEN".equals(candidate.getName()))
                                .findFirst()
                                .orElseThrow();

                String html = browsePageAsHtml("keyword", "Silver PEN");

                assertTrue(html.contains("/products/" + product.getId()));
        }

        @Test
        void detailPageShowsOrderedImagesStockCategoryPriceAndPublishedReviewSummary() throws Exception {
                Product product = productRepository.save(new Product(category, "Detail Notebook",
                                "Ruled A5 notebook", new BigDecimal("7.50"), 4, true));
                productImageRepository.save(new ProductImage(product, "https://example.com/cover-second.jpg", 1));
                productImageRepository.save(new ProductImage(product, "https://example.com/cover-first.jpg", 0));

                String suffix = UUID.randomUUID().toString();
                saveApprovedReview(saveCustomer("first-" + suffix + "@example.com"), product, 5);
                saveApprovedReview(saveCustomer("second-" + suffix + "@example.com"), product, 3);
                reviewRepository.save(new Review(saveCustomer("pending-" + suffix + "@example.com"),
                                product, 1, "Pending review"));

                String html = mockMvc.perform(get("/products/{id}", product.getId())
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/detail"))
                                .andExpect(content().string(containsString("Detail Notebook")))
                                .andExpect(content().string(containsString(category.getName())))
                                .andExpect(content().string(containsString("$7.50")))
                                .andExpect(content().string(containsString("In stock: 4")))
                                .andExpect(content().string(containsString("4.0")))
                                .andExpect(content().string(containsString("out of 5")))
                                .andExpect(content().string(containsString("Based on 2 published reviews")))
                                .andExpect(content().string(not(containsString("Based on 3 published reviews"))))
                                .andReturn().getResponse().getContentAsString();

                assertTrue(html.indexOf("cover-first.jpg") < html.indexOf("cover-second.jpg"));
        }

        @Test
        void detailPageShowsEmptyStatesWhenImagesAndPublishedReviewsAreMissing() throws Exception {
                Product product = productRepository.save(new Product(category, "Bare Product",
                                "No images or reviews", new BigDecimal("2.00"), 0, true));

                mockMvc.perform(get("/products/{id}", product.getId())
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/detail"))
                                .andExpect(content().string(containsString("No images available")))
                                .andExpect(content().string(containsString("No published reviews yet")))
                                .andExpect(content().string(containsString("Out of stock")))
                                .andExpect(content().string(not(containsString("<img"))));
        }

        @Test
        void detailPageIsNotFoundForInactiveAndMissingProducts() throws Exception {
                Product hidden = productRepository.save(new Product(category, "Draft Only",
                                "Hidden draft", new BigDecimal("1.00"), 2, false));

                mockMvc.perform(get("/products/{id}", hidden.getId())
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .accept(MediaType.TEXT_HTML))
                                .andExpect(status().isNotFound())
                                .andExpect(view().name("error/404"))
                                .andExpect(content().string(not(containsString("Draft Only"))));

                mockMvc.perform(get("/products/999999")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .accept(MediaType.TEXT_HTML))
                                .andExpect(status().isNotFound())
                                .andExpect(view().name("error/404"));
        }

}
