package mn.icode.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import mn.icode.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long>{

	List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

	Page<Order> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

	Optional<Order> findByIdAndUserId(Long orderId, Long userId);
}
