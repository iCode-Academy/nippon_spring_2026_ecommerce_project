package mn.icode.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import mn.icode.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long>{
    
}
