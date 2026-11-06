package mn.icode.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import mn.icode.entity.Category;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductRepository;

class CategoryServiceTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        categoryService = new CategoryService(categoryRepository, productRepository);
    }

    private static Category category(Long id, String name) {
        Category category = new Category(name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    // --- Duplicate names are rejected (case-insensitive) ---

    @Test
    void createRejectsDuplicateName() {
        when(categoryRepository.existsByNameIgnoreCase("Stationery")).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.create("Stationery"));

        assertTrue(exception.getMessage().contains("already exists"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        when(categoryRepository.existsByNameIgnoreCase(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0))
                        .equalsIgnoreCase("stationery"));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.create("STATIONERY"));

        assertTrue(exception.getMessage().contains("already exists"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createTrimsNameBeforeSaving() {
        when(categoryRepository.existsByNameIgnoreCase("Stationery")).thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create("  Stationery  ");

        assertEquals("Stationery", created.getName());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void updateRejectsDuplicateNameOwnedByAnotherCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Books")));
        when(categoryRepository.findByNameIgnoreCase("Stationery"))
                .thenReturn(Optional.of(category(9L, "Stationery")));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.update(1L, "Stationery"));

        assertTrue(exception.getMessage().contains("already exists"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateAllowsKeepingTheSameName() {
        Category own = category(1L, "Stationery");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(own));
        when(categoryRepository.findByNameIgnoreCase("Stationery"))
                .thenReturn(Optional.of(own));
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category updated = categoryService.update(1L, "  Stationery ");

        assertEquals("Stationery", updated.getName());
        verify(categoryRepository).save(own);
    }

    @Test
    void updateMissingCategoryThrowsNotFound() {
        when(categoryRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.update(42L, "Anything"));
    }

    // --- Referenced categories cannot be destructively removed ---

    @Test
    void deleteRejectsCategoryWithAssignedProducts() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Stationery")));
        when(productRepository.countByCategoryId(1L)).thenReturn(3L);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.delete(1L));

        assertTrue(exception.getMessage().contains("Cannot delete"));
        assertTrue(exception.getMessage().contains("Stationery"));
        assertTrue(exception.getMessage().contains("3 product(s)"));
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deleteRemovesUnreferencedCategory() {
        Category own = category(1L, "Stationery");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(own));
        when(productRepository.countByCategoryId(1L)).thenReturn(0L);

        Category deleted = categoryService.delete(1L);

        assertEquals("Stationery", deleted.getName());
        verify(categoryRepository).delete(own);
    }

    @Test
    void deleteMissingCategoryThrowsNotFound() {
        when(categoryRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.delete(7L));

        verify(categoryRepository, never()).delete(any(Category.class));
    }

    // --- Listing ---

    @Test
    void findAllReturnsCategoriesOrderedByName() {
        when(categoryRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(category(1L, "Books"), category(2L, "Stationery")));

        List<Category> categories = categoryService.findAll();

        assertEquals(2, categories.size());
        assertEquals("Books", categories.get(0).getName());
        assertFalse(categories.isEmpty());
        verify(categoryRepository).findAllByOrderByNameAsc();
    }

    // --- Blank names never reach the database ---

    @Test
    void createRejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> categoryService.create("   "));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createRejectsNameLongerThan150Characters() {
        String tooLong = "x".repeat(151);

        assertThrows(IllegalArgumentException.class, () -> categoryService.create(tooLong));

        verify(categoryRepository, never()).save(any(Category.class));
    }
}
