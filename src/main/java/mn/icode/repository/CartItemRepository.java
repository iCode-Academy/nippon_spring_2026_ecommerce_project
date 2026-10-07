package mn.icode.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mn.icode.entity.Cart;
import mn.icode.entity.CartItem;
import mn.icode.entity.Product;

public interface CartItemRepository extends JpaRepository<CartItem, Long>{
	
	List<CartItem> findByCartOrderByIdAsc(Cart cart);
	
	Optional<CartItem> findByCartAndBook(Cart cart, Product product);
}