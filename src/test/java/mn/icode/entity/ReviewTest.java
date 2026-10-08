package mn.icode.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import mn.icode.model.ReviewStatus;

public class ReviewTest {
    
    @Test 
    void shouldCreateReviewWithValidRating() {
        User customer = new User();
        Product product = new Product();

        Review review = new Review(customer, product, 5, "Great product");
        assertEquals(5, review.getRating());
        assertEquals("Great product", review.getComment());
        assertEquals(customer, review.getCustomer());
        assertEquals(product, review.getProduct());
    }

    @Test 
    void shouldDefaultModerationStatusPending() {
        Review review = new Review(new User(), new Product(), 4, "Good Product");

        assertEquals(ReviewStatus.PENDING, review.getModerationStatus());
    }

    @Test 
    void shouldRejectRatingBelowOne() {
        assertThrows(
            IllegalArgumentException.class, () -> new Review(new User(), new Product(), 0, "Bad Rating"));
    }

    @Test 
    void shouldRejectRatingAboveFive() {
        assertThrows(
            IllegalArgumentException.class, () -> new Review(new User(), new Product(), 6, "Bad rating"));
    }

    @Test
    void shouldRejectNullRating() {
        assertThrows(IllegalArgumentException.class, 
            () -> new Review(new User(), new Product(), null, "No rating")
        );
    }

    @Test
    void shouldSetCreatedAt() {
        Review review = new Review(new User(), new Product(), 5, "Excellent");

        assertNotNull(review.getCreatedAt());
    }
}
