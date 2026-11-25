package mn.icode.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mn.icode.entity.Review;
import mn.icode.model.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long>{

    long countByProductIdAndModerationStatus(Long productId, ReviewStatus moderationStatus);

    @Query("select avg(r.rating) from Review r " +
           "where r.product.id = :productId and r.moderationStatus = :moderationStatus")
    Double averageRatingByProductIdAndModerationStatus(
            @Param("productId") Long productId,
            @Param("moderationStatus") ReviewStatus moderationStatus);
}
