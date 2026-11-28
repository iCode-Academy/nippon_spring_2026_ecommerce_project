package mn.icode.dto;

import java.util.List;

/**
 * Combined review data for a product detail page or API response.
 *
 * <p>{@code reviews} contains only publicly visible (APPROVED) reviews.
 * {@code currentUserReview} exposes the authenticated customer's own review
 * regardless of moderation state so pending or rejected content can be edited
 * or deleted.</p>
 */
public record ProductReviewsResponse(
        Long productId,
        ReviewSummary summary,
        List<ReviewResponse> reviews,
        ReviewResponse currentUserReview,
        boolean reviewEligible) {
}
