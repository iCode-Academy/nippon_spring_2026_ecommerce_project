package mn.icode.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import mn.icode.dto.ProductReviewsResponse;
import mn.icode.dto.ReviewRequest;
import mn.icode.dto.ReviewResponse;
import mn.icode.service.ReviewService;

/**
 * CAP-31 customer product review endpoints.
 *
 * <p>Public listing returns only APPROVED reviews (the CAP-07 visibility
 * rule). Create, update and delete require the CUSTOMER role and a valid CSRF
 * token; ownership is always derived from the authenticated principal.</p>
 */
@RestController
public class ReviewApiController {

    private final ReviewService reviewService;

    public ReviewApiController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/products/{productId}/reviews")
    public ProductReviewsResponse listReviews(@PathVariable Long productId, Principal principal) {
        return reviewService.getProductReviews(productId, principal == null ? null : principal.getName());
    }

    @PostMapping(
            value = "/api/products/{productId}/reviews",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse createReview(@PathVariable Long productId,
                                       @Valid @RequestBody ReviewRequest request,
                                       Principal principal) {
        return reviewService.createReview(productId, principal.getName(), request);
    }

    @PutMapping(
            value = "/api/reviews/{reviewId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReviewResponse updateReview(@PathVariable Long reviewId,
                                       @Valid @RequestBody ReviewRequest request,
                                       Principal principal) {
        return reviewService.updateReview(reviewId, principal.getName(), request);
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReview(@PathVariable Long reviewId, Principal principal) {
        reviewService.deleteReview(reviewId, principal.getName());
    }
}
