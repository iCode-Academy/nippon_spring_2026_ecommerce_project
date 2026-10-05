package mn.icode.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mn.icode.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
	
	List<ProductImage> findByProductIdOrderByPositionAsc(Long productId);
}
