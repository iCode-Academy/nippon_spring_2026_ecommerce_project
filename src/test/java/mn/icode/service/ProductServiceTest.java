package mn.icode.service;

import java.math.BigDecimal;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

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
        Product product = new Product(category, "Test Product", "Description", new BigDecimal("10.00"), 5, true);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        // Act & Assert: Adjusting stock by -10 should throw IllegalArgumentException
        // since 5 + (-10) = -5 which is below zero
        assertThrows(IllegalArgumentException.class, () -> productService.adjustStock(1L, -10));

        // Verify that product was never saved since exception was thrown before repository call
        verify(productRepository, never()).save(any(Product.class));
    }

}
