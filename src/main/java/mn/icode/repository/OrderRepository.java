package mn.icode.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import mn.icode.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long>{

	List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

	Page<Order> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

	Optional<Order> findByIdAndUserId(Long orderId, Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select o from Order o where o.id = :orderId and o.user.id = :userId
		""")
	Optional<Order> findByIdAndUserIdForUpdate(
		@Param("orderId") Long orderId,
		@Param("userId") Long userId);
}
