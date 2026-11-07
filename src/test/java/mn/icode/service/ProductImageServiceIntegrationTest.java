package mn.icode.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.entity.ProductImage;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductImageRepository;
import mn.icode.repository.ProductRepository;

@SpringBootTest
@ActiveProfiles("postgres-test")
class ProductImageServiceIntegrationTest {

        @Autowired
        private DataSource dataSource;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private ProductImageRepository imageRepository;

        @Autowired
        private ProductImageService imageService;

        @PersistenceContext
        private EntityManager entityManager;

        @Test
        void connectsToTestDatabase() throws Exception {

                try (Connection connection = dataSource.getConnection()) {

                        String databaseName = connection.getCatalog();

                        String expectedDatabase = System.getenv("DB_URL") != null
                                        ? "ecommerce"
                                        : "ecommerce_test";

                        assertEquals(
                                        expectedDatabase,
                                        databaseName,
                                        "Integration tests must connect to the configured database");
                }
        }

        @Test
        @Transactional
        void reorderImages_updatesPositionsInPostgres() {

                // Arrange: create category and product
                Category category = categoryRepository.save(
                                new Category("Integration Test Category"));

                Product product = productRepository.save(
                                new Product(
                                                category,
                                                "Integration Test Product",
                                                "Testing image ordering",
                                                new BigDecimal("15.00"),
                                                10,
                                                true));

                // Create three images
                ProductImage first = imageRepository.saveAndFlush(
                                new ProductImage(product, "https://example.com/1.jpg", 0));

                ProductImage second = imageRepository.saveAndFlush(
                                new ProductImage(product, "https://example.com/2.jpg", 1));

                ProductImage third = imageRepository.saveAndFlush(
                                new ProductImage(product, "https://example.com/3.jpg", 2));

                // Act: reverse the image order
                imageService.reorderImages(
                                product.getId(),
                                List.of(third.getId(), second.getId(), first.getId()));

                // Send all pending changes to PostgreSQL
                entityManager.flush();

                // Clear Hibernate's cached entities
                entityManager.clear();

                // Retrieve fresh entities from PostgreSQL

                // Assert: retrieve the new order from the database
                List<ProductImage> reordered = imageRepository.findByProductIdOrderByPositionAsc(product.getId());

                assertEquals(third.getId(), reordered.get(0).getId());
                assertEquals(second.getId(), reordered.get(1).getId());
                assertEquals(first.getId(), reordered.get(2).getId());

                assertEquals(0, reordered.get(0).getPosition());
                assertEquals(1, reordered.get(1).getPosition());
                assertEquals(2, reordered.get(2).getPosition());
        }

        @Test
        @Transactional
        void deleteImage_middleImage_reordersRemainingInPostgres() {

                // Arrange: create a category
                Category category = categoryRepository.save(
                                new Category("Delete Test Category"));

                // Create a product
                Product product = productRepository.save(
                                new Product(
                                                category,
                                                "Delete Test Product",
                                                "Testing image deletion",
                                                new BigDecimal("20.00"),
                                                5,
                                                true));

                // Create three images
                ProductImage first = imageRepository.saveAndFlush(
                                new ProductImage(product, "https://example.com/1.jpg", 0));

                ProductImage middle = imageRepository.saveAndFlush(
                                new ProductImage(product, "https://example.com/2.jpg", 1));

                ProductImage last = imageRepository.saveAndFlush(
                                new ProductImage(product, "https://example.com/3.jpg", 2));

                Long productId = product.getId();
                Long firstId = first.getId();
                Long middleId = middle.getId();
                Long lastId = last.getId();

                // Act: delete the middle image
                imageService.deleteImage(productId, middleId);

                // Flush changes to PostgreSQL
                entityManager.flush();

                // Clear Hibernate's persistence context
                entityManager.clear();

                // Retrieve remaining images from the database
                List<ProductImage> remaining = imageRepository.findByProductIdOrderByPositionAsc(productId);

                // Assert: only two images remain
                assertEquals(2, remaining.size());

                // First image remains at position 0
                assertEquals(firstId, remaining.get(0).getId());
                assertEquals(0, remaining.get(0).getPosition());

                // Last image moves from position 2 to position 1
                assertEquals(lastId, remaining.get(1).getId());
                assertEquals(1, remaining.get(1).getPosition());

                // Deleted image no longer exists
                assertFalse(imageRepository.existsById(middleId));
        }

        @Test
        @Transactional
        void addImage_afterDeletion_usesNextPositionInPostgres() {

                // Arrange: create category and product
                Category category = categoryRepository.save(
                                new Category("Add After Delete Category"));

                Product product = productRepository.save(
                                new Product(
                                                category,
                                                "Add After Delete Product",
                                                "Testing image positions",
                                                new BigDecimal("25.00"),
                                                10,
                                                true));

                Long productId = product.getId();

                // Add three images using the service
                ProductImage first = imageService.addImage(
                                productId, "https://example.com/1.jpg");

                ProductImage middle = imageService.addImage(
                                productId, "https://example.com/2.jpg");

                ProductImage last = imageService.addImage(
                                productId, "https://example.com/3.jpg");

                // Delete the middle image
                imageService.deleteImage(productId, middle.getId());

                // Add another image
                ProductImage newImage = imageService.addImage(
                                productId, "https://example.com/4.jpg");

                // Persist changes and clear Hibernate's cache
                entityManager.flush();
                entityManager.clear();

                // Read the final image order from PostgreSQL
                List<ProductImage> images = imageRepository.findByProductIdOrderByPositionAsc(productId);

                // Assert: three images remain
                assertEquals(3, images.size());

                // Verify the image order
                assertEquals(first.getId(), images.get(0).getId());
                assertEquals(last.getId(), images.get(1).getId());
                assertEquals(newImage.getId(), images.get(2).getId());

                // Verify consecutive positions
                assertEquals(0, images.get(0).getPosition());
                assertEquals(1, images.get(1).getPosition());
                assertEquals(2, images.get(2).getPosition());

                // Verify deleted image is gone
                assertFalse(imageRepository.existsById(middle.getId()));
        }

}