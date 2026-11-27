package mn.icode.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import mn.icode.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id = :id")
	Optional<Product> findByIdForUpdate(Long id);
	
	List<Product> findByCategoryIdAndActiveTrue(Long categoryId);
	
	List<Product> findByActiveTrue();
	
	boolean existsByCategoryId(Long categoryId);
	
	long countByCategoryId(Long categoryId);

	Page<Product> findByActiveTrue(Pageable pageable);
	
	Page<Product> findByActiveTrueAndNameContainingIgnoreCase(String keyword, Pageable pageable);
	
	Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);
}
