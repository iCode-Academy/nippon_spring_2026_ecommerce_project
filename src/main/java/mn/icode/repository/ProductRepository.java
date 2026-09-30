package mn.icode.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mn.icode.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
	
	List<Product> findByCategoryIdAndActiveTrue(Long categoryId);
	
	List<Product> findByActiveTrue();
	
	boolean eexistsByCategoryId(Long categoryId);
}
