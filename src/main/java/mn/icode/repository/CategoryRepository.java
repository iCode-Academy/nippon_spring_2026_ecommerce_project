package mn.icode.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mn.icode.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
	
	Optional<Category> findByName(String name);
	
	boolean existsByName(String name);
	
	List<Category> findAllByOrderByNameAsc();
	
	Optional<Category> findByNameIgnoreCase(String name);
	
	boolean existsByNameIgnoreCase(String name);
}
