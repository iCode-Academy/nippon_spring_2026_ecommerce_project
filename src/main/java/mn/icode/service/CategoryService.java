package mn.icode.service;

import java.util.List;

import org.springframework.stereotype.Service;

import mn.icode.entity.Category;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    public Category get(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + id));
    }

    public Category create(String name) {
        String normalized = normalize(name);

        if (categoryRepository.existsByNameIgnoreCase(normalized)) {
            throw new ConflictException(
                    "A category named \"" + normalized + "\" already exists");
        }

        return categoryRepository.save(new Category(normalized));
    }

    public Category update(Long id, String name) {
        String normalized = normalize(name);
        Category category = get(id);

        categoryRepository.findByNameIgnoreCase(normalized)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "A category named \"" + normalized + "\" already exists");
                });

        category.setName(normalized);
        return categoryRepository.save(category);
    }

    public Category delete(Long id) {
        Category category = get(id);

        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new ConflictException(
                    "Cannot delete \"" + category.getName() + "\" because "
                    + productCount + " product(s) are assigned to it. "
                    + "Reassign or remove those products first.");
        }

        categoryRepository.delete(category);
        return category;
    }

    private String normalize(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Category name is required");
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (normalized.length() > 150) {
            throw new IllegalArgumentException(
                    "Category name must not exceed 150 characters");
        }
        return normalized;
    }
}
