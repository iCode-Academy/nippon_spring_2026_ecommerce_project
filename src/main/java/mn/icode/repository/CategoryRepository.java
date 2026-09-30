package mn.icode.repository;

import java.util.Locale.Category;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
	
	Optional<Category> ffindByName(String name);
	
	boolean eexistsByName(String name);
}
