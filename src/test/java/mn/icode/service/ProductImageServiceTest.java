package mn.icode.service;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.entity.ProductImage;
import mn.icode.repository.ProductImageRepository;

@ExtendWith(MockitoExtension.class)
public class ProductImageServiceTest {

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductImageService imageService;

    private Product product;

    @BeforeEach
    void setUp() {
        Category category = new Category("Stationery");

        product = new Product(
                category,
                "Notebook",
                "A5 lined notebook",
                new BigDecimal("12.50"),
                100,
                true);
    }

    @Test
    void addImage_success() {

        // Arrange
        Long productId = 1L;
        String imageUrl = "https://example.com/notebook.jpg";

        when(productService.findById(productId))
                .thenReturn(product);

        when(imageRepository.findByProductIdOrderByPositionAsc(productId))
                .thenReturn(List.of());

        when(imageRepository.save(any(ProductImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ProductImage result = imageService.addImage(productId, imageUrl);

        // Assert
        assertNotNull(result);
        assertEquals(imageUrl, result.getImageUrl());
        assertEquals(0, result.getPosition());
        assertEquals(product, result.getProduct());

        verify(imageRepository).save(any(ProductImage.class));
    }

    @Test
    void addImage_withExistingImages() {

        // Arrange
        Long productId = 1L;
        String newImageUrl = "https://example.com/side.jpg";

        ProductImage firstImage = new ProductImage(
                product,
                "https://example.com/front.jpg",
                0);

        ProductImage secondImage = new ProductImage(
                product,
                "https://example.com/back.jpg",
                1);

        when(productService.findById(productId))
                .thenReturn(product);

        when(imageRepository.findByProductIdOrderByPositionAsc(productId))
                .thenReturn(List.of(firstImage, secondImage));

        when(imageRepository.save(any(ProductImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ProductImage result = imageService.addImage(
                productId,
                newImageUrl);

        // Assert
        assertNotNull(result);
        assertEquals(newImageUrl, result.getImageUrl());
        assertEquals(2, result.getPosition());
        assertEquals(product, result.getProduct());

        verify(imageRepository).save(any(ProductImage.class));
    }

    @Test
    void deleteImage_success() {

        // Arrange
        Long productId = 1L;
        Long imageId = 10L;

        ProductImage image = mock(ProductImage.class);

        when(image.getId()).thenReturn(imageId);
        when(image.getPosition()).thenReturn(0);

        when(productService.findById(productId))
                .thenReturn(product);

        when(imageRepository.findByProductIdOrderByPositionAsc(productId))
                .thenReturn(List.of(image));

        // Act
        imageService.deleteImage(productId, imageId);

        // Assert
        verify(imageRepository).delete(image);
        verify(imageRepository).flush();
    }

    @Test
    void deleteImage_middleImage_reordersRemaining() {

        // Arrange
        Long productId = 1L;
        Long imageIdToDelete = 11L;

        ProductImage first = mock(ProductImage.class);
        ProductImage middle = mock(ProductImage.class);
        ProductImage last = mock(ProductImage.class);

        when(first.getId()).thenReturn(10L);
        when(middle.getId()).thenReturn(11L);
        when(last.getId()).thenReturn(12L);

        when(first.getPosition()).thenReturn(0);
        when(middle.getPosition()).thenReturn(1);
        when(last.getPosition()).thenReturn(2);

        when(productService.findById(productId))
                .thenReturn(product);

        when(imageRepository.findByProductIdOrderByPositionAsc(productId))
                .thenReturn(
                        List.of(first, middle, last),
                        List.of(first, last));

        // Act
        imageService.deleteImage(productId, imageIdToDelete);

        // Assert
        verify(imageRepository).delete(middle);
        verify(imageRepository).flush();

        // Move last image to a temporary position.
        verify(last).setPosition(3);

        // Assign its final position.
        verify(last).setPosition(1);
    }

    @Test
    void reorderImages_success() {

        // Arrange
        Long productId = 1L;

        ProductImage first = mock(ProductImage.class);
        ProductImage second = mock(ProductImage.class);
        ProductImage third = mock(ProductImage.class);

        when(first.getId()).thenReturn(10L);
        when(second.getId()).thenReturn(11L);
        when(third.getId()).thenReturn(12L);

        when(first.getPosition()).thenReturn(0);
        when(second.getPosition()).thenReturn(1);
        when(third.getPosition()).thenReturn(2);

        when(productService.findById(productId))
                .thenReturn(product);

        when(imageRepository.findByProductIdOrderByPositionAsc(productId))
                .thenReturn(List.of(first, second, third));

        // Requested order: third, first, second
        List<Long> newOrder = List.of(12L, 10L, 11L);

        // Act
        imageService.reorderImages(productId, newOrder);

        // Assert: temporary positions
        verify(first).setPosition(3);
        verify(second).setPosition(4);
        verify(third).setPosition(5);

        // Assert: final positions
        verify(third).setPosition(0);
        verify(first).setPosition(1);
        verify(second).setPosition(2);

        verify(imageRepository, times(6))
                .saveAndFlush(any(ProductImage.class));
    }

    @Test
    void reorderImages_duplicateIds_throwsException() {

        // Arrange
        Long productId = 1L;

        ProductImage first = mock(ProductImage.class);
        ProductImage second = mock(ProductImage.class);
        ProductImage third = mock(ProductImage.class);

        when(productService.findById(productId))
                .thenReturn(product);

        when(imageRepository.findByProductIdOrderByPositionAsc(productId))
                .thenReturn(List.of(first, second, third));

        // Duplicate image ID 10
        List<Long> invalidOrder = List.of(10L, 10L, 12L);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageService.reorderImages(productId, invalidOrder));

        assertEquals(
                "Invalid image IDs or duplicate IDs",
                exception.getMessage());

        // No image positions should be changed.
        verify(first, never()).setPosition(anyInt());
        verify(second, never()).setPosition(anyInt());
        verify(third, never()).setPosition(anyInt());

        // No database updates should occur.
        verify(imageRepository, never())
                .saveAndFlush(any(ProductImage.class));
    }

    @Test
    void reorderImages_missingImageId_throwsException() {

        // Arrange
        Long productId = 1L;

        ProductImage first = mock(ProductImage.class);
        ProductImage second = mock(ProductImage.class);
        ProductImage third = mock(ProductImage.class);

        when(productService.findById(productId))
                .thenReturn(product);

        when(imageRepository.findByProductIdOrderByPositionAsc(productId))
                .thenReturn(List.of(first, second, third));

        // Only two IDs supplied instead of three
        List<Long> invalidOrder = List.of(10L, 12L);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageService.reorderImages(productId, invalidOrder));

        assertEquals(
                "Image IDs must match the product's existing images",
                exception.getMessage());

        // No database updates should occur
        verify(imageRepository, never())
                .saveAndFlush(any(ProductImage.class));

        // No image positions should be changed
        verify(first, never()).setPosition(anyInt());
        verify(second, never()).setPosition(anyInt());
        verify(third, never()).setPosition(anyInt());
    }

}
