package mn.icode.specification;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.Specification;

import mn.icode.entity.Product;

public class ProductSpecification {
	
	public static Specification<Product> isActive() {
        return (root, query, cb) ->
                cb.isTrue(root.get("active"));
    }
	
	public static Specification<Product> hasCategory(Long categoryId) {
        return (root, query, cb) ->
                cb.equal(root.get("category").get("id"), categoryId);
    }
	
	public static Specification<Product> hasKeyword(String keyword) {
	    return (root, query, cb) -> {
	        return cb.like(
	            cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%");
	    };
	}
	
	public static Specification<Product> minPrice(BigDecimal minPrice) {
	    return (root, query, cb) -> {
	    	return cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), minPrice);
	    };
	}
	
	public static Specification<Product> maxPrice(BigDecimal maxPrice) {
	    return (root, query, cb) -> {
	    	return cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), maxPrice);
	    };
	}
	
	public static Specification<Product> inStock() {
	    return (root, query, cb) -> {
	    	return cb.greaterThan(root.get("stockQuantity"), 0);
	    };
	}
	
	public static Specification<Product> outOfStock() {
	    return (root, query, cb) -> {
	        return cb.equal(root.get("stockQuantity"), 0);
		};
	}
}
