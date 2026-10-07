package mn.icode.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mn.icode.entity.Cart;
import mn.icode.entity.User;



public interface CartRepository extends JpaRepository<Cart, Long>{
	Optional<Cart> findByUser(User user);
	
	Optional<Cart> findByUserId(Long userId);
    
    boolean existsByUserId(Long userId);
}