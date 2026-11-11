package mn.icode.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.dto.ProductForm;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.specification.ProductSpecification;

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
    public Page<Product> findActiveProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }

    @Transactional(readOnly=true)
    public Product findActiveProductById(Long id) {
        Product product = productRepository.findById(id).orElseThrow(
            ()-> new ResourceNotFoundException("Product not found")
        );
        if(!product.isActive()) {
            throw new ResourceNotFoundException("Product not found");
        }
        return product;
    }

    @Transactional(readOnly=true)
    public Product findById(Long id) {
        return productRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Product not found"));
    }
    
    @Transactional(readOnly=true)
    public Page<Product> searchActiveProducts(
            String keyword,
            Pageable pageable) {
    	if(keyword == null || keyword.isBlank()) {
    		return productRepository.findByActiveTrue(pageable);
    	}
    	return productRepository.findByActiveTrueAndNameContainingIgnoreCase(keyword.trim(), pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<Product> filterProducts(
    		String keyword,
    		Long categoryId,
    		BigDecimal minPrice,
    		BigDecimal maxPrice,
    		Boolean inStock,
    		Pageable pageable) {
    	
    	if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
    	    throw new IllegalArgumentException("Minimum price cannot be negative");
    	}
    	
    	if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
    	    throw new IllegalArgumentException("Maximum price cannot be negative");
    	}
    	
    	if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
    	    throw new IllegalArgumentException(
    	        "Minimum price cannot be greater than maximum price"
    	    );
    	}
    	
    	Specification<Product> spec = ProductSpecification.isActive();
    	
    	if(categoryId != null) {
    		spec = spec.and(ProductSpecification.hasCategory(categoryId));
    	}
    	
    	if (minPrice != null) {
    		spec = spec.and(ProductSpecification.minPrice(minPrice));
    	}

    	if (maxPrice != null) {
    		spec = spec.and(ProductSpecification.maxPrice(maxPrice));
    	}
    	
    	if(keyword != null && !keyword.isBlank()) {
    		spec = spec.and(ProductSpecification.hasKeyword(keyword.trim()));
    	}
    	
    	if (inStock != null) {
    		if (inStock) {
    			spec = spec.and(ProductSpecification.inStock());
    		} else {
    			spec = spec.and(ProductSpecification.outOfStock());
    		}
    	}
    	
    	return productRepository.findAll(spec, pageable);
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
    public void toggleActiveStatus(Long id) {
        Product product = findById(id);
        product.setActive(!product.isActive());
        productRepository.save(product);
    }

    @Transactional
    public void adjustStock(Long id, int quantity) {
        Product product = findById(id);

        int newStock = product.getStockQuantity() + quantity;
        if (newStock < 0) {
            throw new IllegalArgumentException("Stock cannot go below zero. Current stock: " + product.getStockQuantity());
        }
        product.setStockQuantity(newStock);
        productRepository.save(product);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(()-> new ResourceNotFoundException("Category not found"));
    }
}
