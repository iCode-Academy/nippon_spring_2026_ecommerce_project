package mn.icode.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import mn.icode.entity.Order;
import mn.icode.model.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order>{
	@EntityGraph(attributePaths = "user")
	Page<Order> findAll(Specification<Order> spec, Pageable pageable);

	@Query("""
			select count(o) from Order o join o.items i
			where o.user.id = :userId
			  and o.status = :status
			  and i.productId = :productId
		""")
	long countOrdersForCustomerContainingProductWithStatus(
		@Param("userId") Long userId,
		@Param("productId") Long productId,
		@Param("status") OrderStatus status);

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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select o from Order o where o.id = :orderId
		""")
	Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);
}
