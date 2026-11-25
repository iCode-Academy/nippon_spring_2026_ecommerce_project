package mn.icode.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.dto.ReviewSummary;
import mn.icode.model.ReviewStatus;
import mn.icode.repository.ReviewRepository;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public ReviewSummary getPublishedSummary(Long productId) {
        long count = reviewRepository.countByProductIdAndModerationStatus(productId, ReviewStatus.APPROVED);

        if (count == 0) {
            return ReviewSummary.empty();
        }

        Double averageRating = reviewRepository.averageRatingByProductIdAndModerationStatus(
                productId, ReviewStatus.APPROVED);

        return new ReviewSummary(count, averageRating);
    }
}
