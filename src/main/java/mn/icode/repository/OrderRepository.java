package mn.icode.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mn.icode.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long>{
	
	List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
