package mn.icode.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import mn.icode.dto.ProductForm;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

        @Mock
        private ProductRepository productRepository;

        @Mock
        private CategoryRepository categoryRepository;

        @InjectMocks
        private ProductService productService;

        private ProductForm form;
        private Category category;

        @BeforeEach
        void setUp() {
                category = new Category("Stationery");

                form = new ProductForm();
                form.setName("Notebook");
                form.setDescription("A5 lined notebook");
                form.setPrice(new BigDecimal("12.50"));
                form.setStockQuantity(100);
                form.setCategoryId(1L);
                form.setActive(true);
        }

        @Test
        void createProduct_success() {

                // Arrange
                when(categoryRepository.findById(1L))
                                .thenReturn(Optional.of(category));

                when(productRepository.save(any(Product.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // Act
                Product result = productService.create(form);

                // Assert
                assertNotNull(result);
                assertEquals("Notebook", result.getName());
                assertEquals(new BigDecimal("12.50"), result.getPrice());
                assertEquals(100, result.getStockQuantity());
                assertEquals(category, result.getCategory());
                assertTrue(result.isActive());

                verify(productRepository).save(any(Product.class));
        }

        @Test
        void createProduct_categoryNotFound() {

                // Arrange
                when(categoryRepository.findById(999L))
                                .thenReturn(Optional.empty());

                form.setCategoryId(999L);

                // Act & Assert
                assertThrows(
                                ResourceNotFoundException.class,
                                () -> productService.create(form));

                // Verify that the product was never saved
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void updateProduct_success() {

                // Arrange: Create an existing product
                Product existingProduct = new Product(
                                category,
                                "Old Notebook",
                                "Old description",
                                new BigDecimal("10.00"),
                                20,
                                true);

                when(productRepository.findById(1L))
                                .thenReturn(Optional.of(existingProduct));

                when(categoryRepository.findById(1L))
                                .thenReturn(Optional.of(category));

                when(productRepository.save(any(Product.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // Update form values
                form.setName("Updated Notebook");
                form.setDescription("200-page notebook");
                form.setPrice(new BigDecimal("15.99"));
                form.setStockQuantity(50);
                form.setActive(false);

                // Act
                Product result = productService.update(1L, form);

                // Assert
                assertEquals("Updated Notebook", result.getName());
                assertEquals("200-page notebook", result.getDescription());
                assertEquals(new BigDecimal("15.99"), result.getPrice());
                assertEquals(50, result.getStockQuantity());
                assertFalse(result.isActive());
                assertEquals(category, result.getCategory());

                verify(productRepository).save(existingProduct);
        }

        @Test
        void updateProduct_productNotFound() {

                // Arrange
                Long productId = 999L;

                when(productRepository.findById(productId))
                                .thenReturn(Optional.empty());

                // Act & Assert
                ResourceNotFoundException exception = assertThrows(
                                ResourceNotFoundException.class,
                                () -> productService.update(productId, form));

                assertEquals("Product not found", exception.getMessage());

                // Verify that no product was saved
                verify(productRepository, never()).save(any(Product.class));

                // Verify that category lookup was not attempted
                verify(categoryRepository, never()).findById(any());
        }

        @Test
        void adjustStock_negativeResult_throwsException() {

                // Arrange: Create a product with low stock
                Product product = new Product(category, "Test Product", "Description", new BigDecimal("10.00"), 5,
                                true);

                when(productRepository.findById(1L))
                                .thenReturn(Optional.of(product));

                // Act & Assert: Adjusting stock by -10 should throw IllegalArgumentException
                // since 5 + (-10) = -5 which is below zero
                assertThrows(IllegalArgumentException.class, () -> productService.adjustStock(1L, -10));

                // Verify that product was never saved since exception was thrown before
                // repository call
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void findActiveProductById_rejectsInactiveProduct() {

                Long productId = 2L;

                Product inactiveProduct = mock(Product.class);
                when(inactiveProduct.isActive()).thenReturn(false);

                when(productRepository.findById(productId))
                                .thenReturn(Optional.of(inactiveProduct));

                assertThrows(
                                ResourceNotFoundException.class,
                                () -> productService.findActiveProductById(productId));

                verify(productRepository).findById(productId);
        }

        @Test
        void findActiveProducts_returnsPaginatedProducts() {

                Pageable pageable = PageRequest.of(0, 10);

                Product activeProduct = mock(Product.class);
                when(activeProduct.isActive()).thenReturn(true);

                Page<Product> productPage = new PageImpl<>(
                                List.of(activeProduct),
                                pageable,
                                1);

                when(productRepository.findByActiveTrue(pageable))
                                .thenReturn(productPage);

                Page<Product> result = productService.findActiveProducts(pageable);

                assertEquals(1, result.getTotalElements());
                assertEquals(1, result.getContent().size());
                assertTrue(result.getContent().get(0).isActive());

                verify(productRepository).findByActiveTrue(pageable);
        }

        @Test
        void searchActiveProducts_nullKeywordListsAllActiveProducts() {

                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Page<Product> expectedPage = new PageImpl<>(List.of(), pageable, 0);

                when(productRepository.findByActiveTrue(pageable))
                                .thenReturn(expectedPage);

                // Act
                Page<Product> result = productService.searchActiveProducts(null, pageable);

                // Assert
                assertEquals(expectedPage, result);
                verify(productRepository).findByActiveTrue(pageable);
                verify(productRepository, never())
                                .findByActiveTrueAndNameContainingIgnoreCase(any(), any(Pageable.class));
        }

        @Test
        void searchActiveProducts_blankKeywordListsAllActiveProducts() {

                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Page<Product> expectedPage = new PageImpl<>(List.of(), pageable, 0);

                when(productRepository.findByActiveTrue(pageable))
                                .thenReturn(expectedPage);

                // Act
                Page<Product> result = productService.searchActiveProducts("   ", pageable);

                // Assert
                assertEquals(expectedPage, result);
                verify(productRepository).findByActiveTrue(pageable);
                verify(productRepository, never())
                                .findByActiveTrueAndNameContainingIgnoreCase(any(), any(Pageable.class));
        }

        @Test
        void searchActiveProducts_trimsKeywordBeforeSearching() {

                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Product product = new Product(category, "Fountain Pen", "Smooth writing",
                                new BigDecimal("12.50"), 5, true);
                Page<Product> expectedPage = new PageImpl<>(List.of(product), pageable, 1);

                when(productRepository.findByActiveTrueAndNameContainingIgnoreCase("pen", pageable))
                                .thenReturn(expectedPage);

                // Act
                Page<Product> result = productService.searchActiveProducts("  pen  ", pageable);

                // Assert
                assertEquals(expectedPage, result);
                verify(productRepository).findByActiveTrueAndNameContainingIgnoreCase("pen", pageable);
        }

        @Test
        void filterProducts_returnsFilteredPageFromRepository() {

                // Arrange
                Pageable pageable = PageRequest.of(0, 12, Sort.by("price").ascending());
                Product product = new Product(category, "Notebook", "A5 lined notebook",
                                new BigDecimal("12.50"), 100, true);
                Page<Product> expectedPage = new PageImpl<>(List.of(product), pageable, 1);

                when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageable)))
                                .thenReturn(expectedPage);

                // Act
                Page<Product> result = productService.filterProducts("notebook", 1L,
                                new BigDecimal("10.00"), new BigDecimal("20.00"), true, pageable);

                // Assert
                assertEquals(expectedPage, result);
                verify(productRepository).findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageable));
        }

        @Test
        void filterProducts_allowsEqualMinAndMaxPrice() {

                // Arrange
                Pageable pageable = PageRequest.of(0, 12);
                Page<Product> expectedPage = new PageImpl<>(List.of(), pageable, 0);

                when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageable)))
                                .thenReturn(expectedPage);

                // Act
                Page<Product> result = productService.filterProducts(null, null,
                                new BigDecimal("10.00"), new BigDecimal("10.00"), null, pageable);

                // Assert
                assertEquals(expectedPage, result);
                verify(productRepository).findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageable));
        }

        @Test
        void filterProducts_rejectsNegativeMinPrice() {

                // Arrange
                Pageable pageable = PageRequest.of(0, 12);

                // Act & Assert
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> productService.filterProducts(null, null,
                                                new BigDecimal("-1.00"), null, null, pageable));

                assertEquals("Minimum price cannot be negative", exception.getMessage());
                verify(productRepository, never())
                                .findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class));
        }

        @Test
        void filterProducts_rejectsNegativeMaxPrice() {

                // Arrange
                Pageable pageable = PageRequest.of(0, 12);

                // Act & Assert
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> productService.filterProducts(null, null,
                                                null, new BigDecimal("-0.01"), null, pageable));

                assertEquals("Maximum price cannot be negative", exception.getMessage());
                verify(productRepository, never())
                                .findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class));
        }

        @Test
        void filterProducts_rejectsMinPriceGreaterThanMaxPrice() {

                // Arrange
                Pageable pageable = PageRequest.of(0, 12);

                // Act & Assert
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> productService.filterProducts(null, null,
                                                new BigDecimal("50.00"), new BigDecimal("10.00"), null, pageable));

                assertEquals("Minimum price cannot be greater than maximum price", exception.getMessage());
                verify(productRepository, never())
                                .findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class));
        }

}
