package mn.icode.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import mn.icode.entity.Review;

/**
 * Public representation of a product review. Customer email addresses are
 * never exposed; only a shortened display name is returned.
 */
public record ReviewResponse(
        Long id,
        Long productId,
        Long customerId,
        String authorName,
        int rating,
        String comment,
        String moderationStatus,
        LocalDateTime createdAt) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getProduct() == null ? null : review.getProduct().getId(),
                review.getCustomer() == null ? null : review.getCustomer().getId(),
                displayName(review),
                review.getRating() == null ? 0 : review.getRating(),
                review.getComment(),
                review.getModerationStatus() == null ? null : review.getModerationStatus().name(),
                review.getCreatedAt());
    }

    public String ratingStars() {
        int filled = Math.max(0, Math.min(5, rating));
        return "★".repeat(filled) + "☆".repeat(5 - filled);
    }

    public String createdAtDisplay() {
        return createdAt == null ? "" : createdAt.format(DATE_FORMAT);
    }

    private static String displayName(Review review) {
        if (review.getCustomer() == null) {
            return "Customer";
        }

        String firstName = review.getCustomer().getFirstName();
        String lastName = review.getCustomer().getLastName();

        if (firstName == null || firstName.isBlank()) {
            return "Customer";
        }

        String trimmedFirst = firstName.trim();
        if (lastName == null || lastName.isBlank()) {
            return trimmedFirst;
        }

        return trimmedFirst + " " + lastName.trim().charAt(0) + ".";
    }
}
