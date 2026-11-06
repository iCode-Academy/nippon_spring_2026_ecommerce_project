package mn.icode.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.dto.ProductForm;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductRepository;

@Service 
public class ProductService {
    
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly=true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly=true)
    public Product findById(Long id) {
        return productRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Product not found"));
    }

    @Transactional 
    public Product create(ProductForm form) {
        Category category = findCategory(form.getCategoryId());

        Product product = new Product(category, form.getName(), form.getDescription(), form.getPrice(), form.getStockQuantity(), form.isActive());

        return productRepository.save(product);
    }

    @Transactional 
    public Product update(Long id, ProductForm form) {
        Product product = findById(id);
        Category category = findCategory(form.getCategoryId());

        product.setCategory(category);
        product.setName(form.getName());
        product.setDescription(form.getDescription());
        product.setPrice(form.getPrice());
        product.setStockQuantity(form.getStockQuantity());
        product.setActive(form.isActive());

        return productRepository.save(product);
    }

    @Transactional 
    public void delete(Long id) {
        Product product = findById(id);
        product.setActive(false);
        productRepository.delete(product);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(()-> new ResourceNotFoundException("Category not found"));
    }
}
