package mn.icode.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import mn.icode.config.SecurityConfig;
import mn.icode.controller.ProductPageController;
import mn.icode.dto.ReviewSummary;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.entity.ProductImage;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.CategoryRepository;
import mn.icode.service.ProductImageService;
import mn.icode.service.ProductService;
import mn.icode.service.ReviewService;

@WebMvcTest(ProductPageController.class)
@Import(SecurityConfig.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class ProductPageControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ProductService productService;

        @MockitoBean
        private CategoryRepository categoryRepository;

        @MockitoBean
        private ProductImageService productImageService;

        @MockitoBean
        private ReviewService reviewService;

        private Category stationery;
        private Product fountainPen;

        @BeforeEach
        void setUp() {
                stationery = categoryWithId(1L, "Stationery");
                fountainPen = new Product(stationery, "Fountain Pen", "Smooth writing",
                                new BigDecimal("12.50"), 5, true);
        }

        private static Category categoryWithId(Long id, String name) {
                Category category = new Category(name);
                ReflectionTestUtils.setField(category, "id", id);
                return category;
        }

        private Page<Product> pageContaining(Product... products) {
                return new PageImpl<>(List.of(products), PageRequest.of(0, 12), products.length);
        }

        private Sort capturedSortFor(String sortParam) throws Exception {
                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(pageContaining());

                mockMvc.perform(get("/products")
                                .param("sort", sortParam)
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk());

                ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
                verify(productService).filterProducts(any(), any(), any(), any(), any(), captor.capture());

                return captor.getValue().getSort();
        }

        // --- Access control ---

        @Test
        void anonymousUsersAreSentToLoginForBrowsePage() throws Exception {
                mockMvc.perform(get("/products"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/login"));

                verify(productService, never())
                                .filterProducts(any(), any(), any(), any(), any(), any(Pageable.class));
        }

        // --- Browse view rendering ---

        @Test
        void listProducts_rendersBrowseViewWithProductCards() throws Exception {
                ReflectionTestUtils.setField(fountainPen, "id", 1L);
                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(pageContaining(fountainPen));
                when(categoryRepository.findAll()).thenReturn(List.of(stationery));

                mockMvc.perform(get("/products").with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/list"))
                                .andExpect(model().attribute("products", List.of(fountainPen)))
                                .andExpect(content().string(containsString("Fountain Pen")))
                                .andExpect(content().string(containsString("Stationery")))
                                .andExpect(content().string(containsString("$12.50")))
                                .andExpect(content().string(containsString("In stock: 5")))
                                .andExpect(content().string(containsString("js-add-to-cart")));
        }

        @Test
        void listProducts_showsEmptyStateWhenNoProductsMatch() throws Exception {
                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(pageContaining());
                when(categoryRepository.findAll()).thenReturn(List.of());

                mockMvc.perform(get("/products").with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/list"))
                                .andExpect(model().attribute("products", List.of()))
                                .andExpect(content().string(containsString("No products found")));
        }

        @Test
        void listProducts_listsCategoriesForFilterDropdown() throws Exception {
                Category electronics = categoryWithId(2L, "Electronics");

                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(pageContaining());
                when(categoryRepository.findAll()).thenReturn(List.of(stationery, electronics));

                mockMvc.perform(get("/products").with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(model().attribute("categories", List.of(stationery, electronics)))
                                .andExpect(content().string(containsString("Electronics")));
        }

        @Test
        void listProducts_exposesPaginationAttributesToView() throws Exception {
                Page<Product> page = new PageImpl<>(List.of(fountainPen), PageRequest.of(1, 12), 13);

                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(page);
                when(categoryRepository.findAll()).thenReturn(List.of());

                mockMvc.perform(get("/products")
                                .param("page", "1")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(model().attribute("currentPage", 1))
                                .andExpect(model().attribute("totalPages", 2))
                                .andExpect(content().string(containsString("aria-label=\"Product pagination\"")))
                                .andExpect(content().string(containsString("aria-current=\"page\"")));
        }

        // --- Pagination passed to service ---

        @Test
        void listProducts_usesDefaultPaginationAndSorting() throws Exception {
                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(pageContaining());
                when(categoryRepository.findAll()).thenReturn(List.of());

                mockMvc.perform(get("/products").with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk());

                ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
                verify(productService).filterProducts(isNull(), isNull(), isNull(), isNull(), isNull(),
                                captor.capture());

                Pageable pageable = captor.getValue();

                assertEquals(0, pageable.getPageNumber());
                assertEquals(12, pageable.getPageSize());
                assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("id").getDirection());
        }

        @Test
        void listProducts_passesRequestedPageNumberToService() throws Exception {
                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(pageContaining());
                when(categoryRepository.findAll()).thenReturn(List.of());

                mockMvc.perform(get("/products")
                                .param("page", "3")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk());

                ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
                verify(productService).filterProducts(any(), any(), any(), any(), any(), captor.capture());

                assertEquals(3, captor.getValue().getPageNumber());
        }

        @Test
        void listProducts_clampsNegativePageNumberToZero() throws Exception {
                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(pageContaining());
                when(categoryRepository.findAll()).thenReturn(List.of());

                mockMvc.perform(get("/products")
                                .param("page", "-5")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk());

                ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
                verify(productService).filterProducts(any(), any(), any(), any(), any(), captor.capture());

                assertEquals(0, captor.getValue().getPageNumber());
        }

        // --- Sorting ---

        @Test
        void listProducts_sortsByPriceAscendingWhenRequested() throws Exception {
                Sort sort = capturedSortFor("priceAsc");

                assertEquals(Sort.Direction.ASC, sort.getOrderFor("price").getDirection());
                assertEquals(Sort.Direction.ASC, sort.getOrderFor("id").getDirection());
        }

        @Test
        void listProducts_sortsByPriceDescendingWhenRequested() throws Exception {
                Sort sort = capturedSortFor("priceDesc");

                assertEquals(Sort.Direction.DESC, sort.getOrderFor("price").getDirection());
                assertEquals(Sort.Direction.ASC, sort.getOrderFor("id").getDirection());
        }

        @Test
        void listProducts_sortsByNewestFirstWhenRequested() throws Exception {
                Sort sort = capturedSortFor("newest");

                assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
                assertEquals(Sort.Direction.DESC, sort.getOrderFor("id").getDirection());
        }

        @Test
        void listProducts_fallsBackToDefaultSortingForUnknownSortValue() throws Exception {
                Sort sort = capturedSortFor("bogus");

                assertEquals(Sort.Direction.ASC, sort.getOrderFor("id").getDirection());
                assertNull(sort.getOrderFor("price"));
                assertNull(sort.getOrderFor("createdAt"));
        }

        // --- Search and filter criteria forwarding ---

        @Test
        void listProducts_forwardsSearchAndFilterCriteriaToService() throws Exception {
                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenReturn(pageContaining());
                when(categoryRepository.findAll()).thenReturn(List.of());

                mockMvc.perform(get("/products")
                                .param("keyword", "pen")
                                .param("categoryId", "1")
                                .param("minPrice", "5.00")
                                .param("maxPrice", "50.00")
                                .param("inStock", "true")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk());

                verify(productService).filterProducts(
                                eq("pen"),
                                eq(1L),
                                eq(new BigDecimal("5.00")),
                                eq(new BigDecimal("50.00")),
                                eq(true),
                                any(Pageable.class));
        }

        // --- Friendly error state ---

        @Test
        void listProducts_rendersErrorMessageForInvalidPriceRange() throws Exception {
                when(productService.filterProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
                                .thenThrow(new IllegalArgumentException(
                                                "Minimum price cannot be greater than maximum price"));
                when(categoryRepository.findAll()).thenReturn(List.of());

                mockMvc.perform(get("/products")
                                .param("minPrice", "50.00")
                                .param("maxPrice", "10.00")
                                .with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/list"))
                                .andExpect(model().attribute("errorMessage",
                                                "Minimum price cannot be greater than maximum price"))
                                .andExpect(model().attribute("products", List.of()))
                                .andExpect(model().attribute("currentPage", 0))
                                .andExpect(model().attribute("totalPages", 0))
                                .andExpect(content().string(containsString(
                                                "Minimum price cannot be greater than maximum price")));
        }

        @Test
        void anonymousUsersAreSentToLoginForDetailPage() throws Exception {
                mockMvc.perform(get("/products/1"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/login"));

                verifyNoInteractions(productService, productImageService, reviewService);
        }

        @Test
        void showProduct_rendersDetailWithOrderedImagesAndPublishedReviewSummary() throws Exception {
                ReflectionTestUtils.setField(fountainPen, "id", 1L);
                ProductImage secondImage = new ProductImage(fountainPen, "https://example.com/second.jpg", 0);
                ProductImage firstImage = new ProductImage(fountainPen, "https://example.com/first.jpg", 1);
                ReviewSummary summary = new ReviewSummary(2, 4.5);

                when(productService.findActiveProductById(1L)).thenReturn(fountainPen);
                when(productImageService.findByProductId(1L)).thenReturn(List.of(secondImage, firstImage));
                when(reviewService.getPublishedSummary(1L)).thenReturn(summary);

                String html = mockMvc.perform(get("/products/1").with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/detail"))
                                .andExpect(model().attribute("product", fountainPen))
                                .andExpect(model().attribute("images", List.of(secondImage, firstImage)))
                                .andExpect(model().attribute("reviewSummary", summary))
                                .andExpect(content().string(containsString("Fountain Pen")))
                                .andExpect(content().string(containsString("Stationery")))
                                .andExpect(content().string(containsString("$12.50")))
                                .andExpect(content().string(containsString("In stock: 5")))
                                .andExpect(content().string(containsString("https://example.com/second.jpg")))
                                .andExpect(content().string(containsString("https://example.com/first.jpg")))
                                .andExpect(content().string(containsString("4.5")))
                                .andExpect(content().string(containsString("out of 5")))
                                .andExpect(content().string(containsString("Based on 2 published reviews")))
                                .andReturn().getResponse().getContentAsString();

                assertTrue(html.indexOf("second.jpg") < html.indexOf("first.jpg"));
                assertTrue(html.contains("id=\"cart-quantity\""));
                assertTrue(html.contains("data-product-id=\"1\""));
                assertTrue(html.contains("/js/add-to-cart.js"));
                verify(productService).findActiveProductById(1L);
                verify(productImageService).findByProductId(1L);
                verify(reviewService).getPublishedSummary(1L);
        }

        @Test
        void showProduct_showsEmptyStatesForProductWithoutImagesOrPublishedReviews() throws Exception {
                ReflectionTestUtils.setField(fountainPen, "id", 2L);

                when(productService.findActiveProductById(2L)).thenReturn(fountainPen);
                when(productImageService.findByProductId(2L)).thenReturn(List.of());
                when(reviewService.getPublishedSummary(2L)).thenReturn(ReviewSummary.empty());

                mockMvc.perform(get("/products/2").with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(view().name("products/detail"))
                                .andExpect(content().string(containsString("No images available")))
                                .andExpect(content().string(containsString("No published reviews yet")))
                                .andExpect(content().string(not(containsString("<img"))));
        }

        @Test
        void showProduct_marksOutOfStockProductAsUnavailable() throws Exception {
                ReflectionTestUtils.setField(fountainPen, "id", 3L);
                fountainPen.setStockQuantity(0);

                when(productService.findActiveProductById(3L)).thenReturn(fountainPen);
                when(productImageService.findByProductId(3L)).thenReturn(List.of());
                when(reviewService.getPublishedSummary(3L)).thenReturn(ReviewSummary.empty());

                mockMvc.perform(get("/products/3").with(user("nora@example.com").roles("CUSTOMER")))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Out of stock")))
                                .andExpect(content().string(containsString("Currently unavailable.")))
                                .andExpect(content().string(containsString("disabled=\"disabled\"")))
                                .andExpect(content().string(not(containsString("In stock:"))));
        }

        @Test
        void showProduct_rendersNotFoundPageForMissingOrInactiveProduct() throws Exception {
                when(productService.findActiveProductById(999L))
                                .thenThrow(new ResourceNotFoundException("Product not found"));

                mockMvc.perform(get("/products/999")
                                .with(user("nora@example.com").roles("CUSTOMER"))
                                .accept(MediaType.TEXT_HTML))
                                .andExpect(status().isNotFound())
                                .andExpect(view().name("error/404"));

                verifyNoInteractions(productImageService, reviewService);
        }

}
