
package mn.icode.controller;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.transaction.Transactional;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.entity.ProductImage;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductImageRepository;
import mn.icode.repository.ProductRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Test
    void adminCanAccessProducts() throws Exception {

        mockMvc.perform(
                get("/api/admin/products")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void regularUserCannotAccessProducts() throws Exception {

        mockMvc.perform(
                get("/api/admin/products")
                        .with(user("customer").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotCreateProductWithoutCsrf() throws Exception {

        mockMvc.perform(
                post("/api/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                    "name": "Notebook",
                                    "description": "A5 lined notebook",
                                    "price": 12.50,
                                    "stockQuantity": 100,
                                    "categoryId": 1,
                                    "active": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminCanCreateProductWithCsrf() throws Exception {

        // Arrange: create a valid category
        Category category = categoryRepository.save(
                new Category("CSRF Test Category"));

        // Act: submit POST request as ADMIN with CSRF
        mockMvc.perform(
                post("/api/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "name": "Notebook",
                                    "description": "A5 lined notebook",
                                    "price": 12.50,
                                    "stockQuantity": 100,
                                    "categoryId": %d,
                                    "active": true
                                }
                                """.formatted(category.getId())))
                // Assert
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Notebook"))
                .andExpect(jsonPath("$.price").value(12.50))
                .andExpect(jsonPath("$.categoryId").value(category.getId()));
    }

    @Test
    void regularUserCannotCreateProductWithCsrf() throws Exception {

        // Act: regular USER sends POST request with valid CSRF
        mockMvc.perform(
                post("/api/admin/products")
                        .with(user("customer").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "name": "Notebook",
                                    "description": "A5 lined notebook",
                                    "price": 12.50,
                                    "stockQuantity": 100,
                                    "categoryId": 1,
                                    "active": true
                                }
                                """))
                // Assert: access denied
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotAccessProducts() throws Exception {

        mockMvc.perform(
                get("/api/admin/products"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));
    }

    @Test
    void regularUserCannotAccessProductImages() throws Exception {

        // Arrange: simulate a regular USER
        Long productId = 1L;

        // Act: request product images
        mockMvc.perform(
                get("/api/admin/products/{productId}/images", productId)
                        .with(user("customer").roles("USER")))
                // Assert: access denied
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUserCannotAddProductImageWithCsrf() throws Exception {

        // Arrange
        Long productId = 1L;

        // Act: regular USER tries to add an image
        mockMvc.perform(
                post("/api/admin/products/{productId}/images", productId)
                        .with(user("customer").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "imageUrl": "https://example.com/notebook.jpg"
                                }
                                """))
                // Assert: access denied
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminCanAddProductImageWithCsrf() throws Exception {

        // Arrange: create a category
        Category category = categoryRepository.save(
                new Category("Image Security Test Category"));

        // Create a product belonging to the category
        Product product = productRepository.save(
                new Product(
                        category,
                        "Image Security Test Product",
                        "Testing ADMIN image creation",
                        new BigDecimal("20.00"),
                        10,
                        true));

        // Act: ADMIN adds an image with a valid CSRF token
        mockMvc.perform(
                post("/api/admin/products/{productId}/images",
                        product.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "imageUrl": "https://example.com/notebook.jpg"
                                }
                                """))
                // Assert: image created successfully
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl")
                        .value("https://example.com/notebook.jpg"))
                .andExpect(jsonPath("$.position").value(0));
    }

    @Test
    @Transactional
    void adminCannotAddImageWithInvalidUrl() throws Exception {

        // Arrange: create a category
        Category category = categoryRepository.save(
                new Category("Invalid Image URL Test Category"));

        // Create a product
        Product product = productRepository.save(
                new Product(
                        category,
                        "Invalid URL Test Product",
                        "Testing image URL validation",
                        new BigDecimal("20.00"),
                        10,
                        true));

        // Act: ADMIN submits an invalid URL
        mockMvc.perform(
                post("/api/admin/products/{productId}/images",
                        product.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "imageUrl": "not-a-valid-url"
                                }
                                """))
                // Assert: validation rejects the request
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void adminCannotAddImageWithHttpUrl() throws Exception {

        // Arrange: create a category
        Category category = categoryRepository.save(
                new Category("HTTP URL Test Category"));

        // Create a product
        Product product = productRepository.save(
                new Product(
                        category,
                        "HTTP URL Test Product",
                        "Testing HTTPS validation",
                        new BigDecimal("20.00"),
                        10,
                        true));

        // Act: ADMIN submits an HTTP URL
        mockMvc.perform(
                post("/api/admin/products/{productId}/images",
                        product.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "imageUrl": "http://example.com/notebook.jpg"
                                }
                                """))
                // Assert: HTTP is not allowed
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void adminCannotCreateProductWithZeroPrice() throws Exception {

        // Arrange: create a valid category
        Category category = categoryRepository.save(
                new Category("Zero Price Test Category"));

        // Act: ADMIN tries to create a product with price = 0
        mockMvc.perform(
                post("/api/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "name": "Notebook",
                                    "description": "A5 lined notebook",
                                    "price": 0,
                                    "stockQuantity": 100,
                                    "categoryId": %d,
                                    "active": true
                                }
                                """.formatted(category.getId())))
                // Assert: invalid price returns HTTP 400
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void adminCannotCreateProductWithNegativePrice() throws Exception {

        // Arrange: create a valid category
        Category category = categoryRepository.save(
                new Category("Negative Price Test Category"));

        // Act: ADMIN submits a negative price
        mockMvc.perform(
                post("/api/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "name": "Notebook",
                                    "description": "A5 lined notebook",
                                    "price": -10.00,
                                    "stockQuantity": 100,
                                    "categoryId": %d,
                                    "active": true
                                }
                                """.formatted(category.getId())))
                // Assert: negative price is rejected
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void adminCannotCreateProductWithInvalidCategory() throws Exception {

        // Arrange: use a category ID that does not exist
        Long invalidCategoryId = 999999L;

        // Act: ADMIN tries to create a product
        mockMvc.perform(
                post("/api/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "name": "Notebook",
                                    "description": "A5 lined notebook",
                                    "price": 12.50,
                                    "stockQuantity": 100,
                                    "categoryId": %d,
                                    "active": true
                                }
                                """.formatted(invalidCategoryId)))
                // Assert: category does not exist
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void adminCannotCreateProductWithoutCategoryId() throws Exception {

        // Act: ADMIN submits a product without categoryId
        mockMvc.perform(
                post("/api/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                    "name": "Notebook",
                                    "description": "A5 lined notebook",
                                    "price": 12.50,
                                    "stockQuantity": 100,
                                    "active": true
                                }
                                """))
                // Assert: missing categoryId returns HTTP 400
                .andExpect(status().isBadRequest());
    }

    @Test
    void regularUserCannotDeleteProductImage() throws Exception {

        // Arrange: simulate a regular USER
        Long productId = 1L;
        Long imageId = 1L;

        // Act: USER attempts to delete a product image
        mockMvc.perform(
                delete("/api/admin/products/{productId}/images/{imageId}",
                        productId, imageId)
                        .with(user("customer").roles("USER"))
                        .with(csrf()))
                // Assert: regular USER cannot delete images
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotDeleteProductImageWithoutCsrf() throws Exception {

        // Arrange: use example product and image IDs
        Long productId = 1L;
        Long imageId = 1L;

        // Act: ADMIN attempts to delete without CSRF
        mockMvc.perform(
                delete("/api/admin/products/{productId}/images/{imageId}",
                        productId, imageId)
                        .with(user("admin").roles("ADMIN")))
                // Assert: request is forbidden
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminCanDeleteProductImageWithCsrf() throws Exception {

        // Arrange: create a category
        Category category = categoryRepository.save(
                new Category("Image Delete Test Category"));

        // Create a product
        Product product = productRepository.save(
                new Product(
                        category,
                        "Image Delete Test Product",
                        "Testing image deletion",
                        new BigDecimal("20.00"),
                        10,
                        true));

        // Create an image belonging to the product
        ProductImage image = productImageRepository.save(
                new ProductImage(
                        product,
                        "https://example.com/notebook.jpg",
                        0));

        Long imageId = image.getId();

        // Act: ADMIN deletes the image with a valid CSRF token
        mockMvc.perform(
                delete("/api/admin/products/{productId}/images/{imageId}",
                        product.getId(), imageId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                // Assert: deletion succeeds
                .andExpect(status().isNoContent());

        // Verify: the image no longer exists
        assertFalse(productImageRepository.existsById(imageId));
    }

    @Test
    void regularUserCannotReorderProductImages() throws Exception {

        // Arrange: simulate a regular USER
        Long productId = 1L;

        // Act: USER attempts to reorder images
        mockMvc.perform(
                put("/api/admin/products/{productId}/images/order",
                        productId)
                        .with(user("customer").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                [2, 1]
                                """))
                // Assert: USER does not have permission
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotReorderProductImagesWithoutCsrf() throws Exception {

        // Arrange: use an example product ID
        Long productId = 1L;

        // Act: ADMIN attempts to reorder images without CSRF
        mockMvc.perform(
                put("/api/admin/products/{productId}/images/order",
                        productId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                [2, 1]
                                """))
                // Assert: missing CSRF token causes HTTP 403
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminCanReorderProductImagesWithCsrf() throws Exception {

        // Arrange: create a category and product
        Category category = categoryRepository.save(
                new Category("Image Reorder Test Category"));

        Product product = productRepository.save(
                new Product(
                        category,
                        "Image Reorder Test Product",
                        "Testing image reordering",
                        new BigDecimal("20.00"),
                        10,
                        true));

        // Create three images in their original order
        ProductImage imageA = productImageRepository.save(
                new ProductImage(product, "https://example.com/a.jpg", 0));

        ProductImage imageB = productImageRepository.save(
                new ProductImage(product, "https://example.com/b.jpg", 1));

        ProductImage imageC = productImageRepository.save(
                new ProductImage(product, "https://example.com/c.jpg", 2));

        // Act: ADMIN requests the order B, C, A
        mockMvc.perform(
                put("/api/admin/products/{productId}/images/order",
                        product.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")

                        .content("""
                                {
                                    "imageIds": [%d, %d, %d]
                                }
                                """.formatted(
                                imageB.getId(),
                                imageC.getId(),
                                imageA.getId()))

        )
                .andExpect(status().isNoContent());

        // Assert: verify the new positions
        assertEquals(
                0,
                productImageRepository.findById(imageB.getId())
                        .orElseThrow().getPosition());

        assertEquals(
                1,
                productImageRepository.findById(imageC.getId())
                        .orElseThrow().getPosition());

        assertEquals(
                2,
                productImageRepository.findById(imageA.getId())
                        .orElseThrow().getPosition());
    }


@Test
@Transactional
void adminCannotReorderImagesWithDuplicateIds() throws Exception {

    // Arrange: create a category and product
    Category category = categoryRepository.save(
            new Category("Duplicate Image Test Category")
    );

    Product product = productRepository.save(
            new Product(
                    category,
                    "Duplicate Image Test Product",
                    "Testing duplicate image IDs",
                    new BigDecimal("20.00"),
                    10,
                    true
            )
    );

    // Create two images
    ProductImage imageA = productImageRepository.save(
            new ProductImage(product, "https://example.com/a.jpg", 0)
    );

    ProductImage imageB = productImageRepository.save(
            new ProductImage(product, "https://example.com/b.jpg", 1)
    );

    // Act: submit image A twice
    mockMvc.perform(
            put("/api/admin/products/{productId}/images/order",
                    product.getId())
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType("application/json")
                    .content("""
                        {
                            "imageIds": [%d, %d]
                        }
                        """.formatted(
                            imageA.getId(),
                            imageA.getId()
                    ))
    )
    // Assert: duplicate IDs are rejected
    .andExpect(status().isBadRequest());

    // Verify: original positions remain unchanged
    assertEquals(
            0,
            productImageRepository.findById(imageA.getId())
                    .orElseThrow().getPosition()
    );

    assertEquals(
            1,
            productImageRepository.findById(imageB.getId())
                    .orElseThrow().getPosition()
    );
}


@Test
@Transactional
void adminCannotReorderImageFromAnotherProduct() throws Exception {

    // Arrange: create a category
    Category category = categoryRepository.save(
            new Category("Cross Product Image Test")
    );

    // Create two different products
    Product productA = productRepository.save(
            new Product(
                    category,
                    "Product A",
                    "First product",
                    new BigDecimal("20.00"),
                    10,
                    true
            )
    );

    Product productB = productRepository.save(
            new Product(
                    category,
                    "Product B",
                    "Second product",
                    new BigDecimal("30.00"),
                    5,
                    true
            )
    );

    // Create an image for each product
    ProductImage imageA = productImageRepository.save(
            new ProductImage(productA, "https://example.com/a.jpg", 0)
    );

    ProductImage imageB = productImageRepository.save(
            new ProductImage(productB, "https://example.com/b.jpg", 0)
    );

    // Act: attempt to reorder Product A using Product B's image
    mockMvc.perform(
            put("/api/admin/products/{productId}/images/order",
                    productA.getId())
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType("application/json")
                    .content("""
                        {
                            "imageIds": [%d]
                        }
                        """.formatted(imageB.getId()))
    )
    // Assert: reject the invalid image ID
    .andExpect(status().isBadRequest());

    // Verify: both images remain unchanged
    assertEquals(
            0,
            productImageRepository.findById(imageA.getId())
                    .orElseThrow().getPosition()
    );

    assertEquals(
            0,
            productImageRepository.findById(imageB.getId())
                    .orElseThrow().getPosition()
    );
}


@Test
@Transactional
void adminCannotReorderNonexistentImage() throws Exception {

    // Arrange: create a category
    Category category = categoryRepository.save(
            new Category("Missing Image Test Category")
    );

    // Create a product
    Product product = productRepository.save(
            new Product(
                    category,
                    "Missing Image Test Product",
                    "Testing nonexistent image ID",
                    new BigDecimal("20.00"),
                    10,
                    true
            )
    );

    // Create one existing image
    ProductImage image = productImageRepository.save(
            new ProductImage(
                    product,
                    "https://example.com/a.jpg",
                    0
            )
    );

    // Choose an image ID that does not exist
    Long nonexistentImageId = 999999L;

    // Act: attempt to reorder using the nonexistent ID
    mockMvc.perform(
            put("/api/admin/products/{productId}/images/order",
                    product.getId())
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType("application/json")
                    .content("""
                        {
                            "imageIds": [%d]
                        }
                        """.formatted(nonexistentImageId))
    )
    // Assert: invalid reorder request
    .andExpect(status().isBadRequest());

    // Verify: existing image position remains unchanged
    assertEquals(
            0,
            productImageRepository.findById(image.getId())
                    .orElseThrow().getPosition()
    );
}


}
