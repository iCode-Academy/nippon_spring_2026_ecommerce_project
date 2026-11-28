package mn.icode.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.dto.ProductReviewsResponse;
import mn.icode.dto.ReviewRequest;
import mn.icode.dto.ReviewResponse;
import mn.icode.dto.ReviewSummary;
import mn.icode.entity.Product;
import mn.icode.entity.Review;
import mn.icode.entity.User;
import mn.icode.exception.ConflictException;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.model.OrderStatus;
import mn.icode.model.ReviewStatus;
import mn.icode.model.Role;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.ReviewRepository;
import mn.icode.repository.UserRepository;

@Service
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository,
                         OrderRepository orderRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public ReviewSummary getPublishedSummary(Long productId) {
        return buildPublishedSummary(productId);
    }

    /**
     * Public listing rule from CAP-07: only reviews whose moderation status is
     * APPROVED are visible to customers and visitors.
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listPublishedReviews(Long productId) {
        requireActiveProduct(productId);
        return buildPublishedReviews(productId);
    }

    /**
     * Returns the authenticated customer's own review, including PENDING or
     * REJECTED content, or {@code null} when the viewer is not an active
     * customer or has not reviewed this product.
     */
    @Transactional(readOnly = true)
    public ReviewResponse findCurrentUserReview(Long productId, String email) {
        return findCurrentUserReviewInternal(productId, email);
    }

    /**
     * Approved eligibility rule: the customer must have a delivered order
     * containing this product (CAP-26 order history/detail data).
     */
    @Transactional(readOnly = true)
    public boolean isEligibleToReview(Long productId, String email) {
        User customer = findActiveCustomer(email);
        if (customer == null) {
            return false;
        }
        return orderRepository.countOrdersForCustomerContainingProductWithStatus(
                customer.getId(), productId, OrderStatus.DELIVERED) > 0;
    }

    @Transactional(readOnly = true)
    public ProductReviewsResponse getProductReviews(Long productId, String email) {
        requireActiveProduct(productId);

        ReviewSummary summary = buildPublishedSummary(productId);
        List<ReviewResponse> reviews = buildPublishedReviews(productId);
        ReviewResponse currentUserReview = findCurrentUserReviewInternal(productId, email);
        boolean eligible = isEligibleToReview(productId, email);

        return new ProductReviewsResponse(
                productId, summary, reviews, currentUserReview, eligible);
    }

    @Transactional
    public ReviewResponse createReview(Long productId, String email, ReviewRequest request) {
        User customer = requireCurrentCustomer(email);
        Product product = requireActiveProduct(productId);

        int rating = requireValidRating(request.rating());
        String comment = normalizeComment(request.comment());

        if (reviewRepository.existsByCustomerIdAndProductId(customer.getId(), productId)) {
            throw new ConflictException("You have already reviewed this product");
        }

        if (orderRepository.countOrdersForCustomerContainingProductWithStatus(
                customer.getId(), productId, OrderStatus.DELIVERED) == 0) {
            throw new ConflictException(
                    "Only customers with a delivered order containing this product can write a review");
        }

        Review review = new Review(customer, product, rating, comment);

        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("You have already reviewed this product");
        }

        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, String email, ReviewRequest request) {
        User customer = requireCurrentCustomer(email);

        int rating = requireValidRating(request.rating());
        String comment = normalizeComment(request.comment());

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        requireOwner(review, customer);

        review.update(rating, comment);
        return ReviewResponse.from(reviewRepository.saveAndFlush(review));
    }

    @Transactional
    public void deleteReview(Long reviewId, String email) {
        User customer = requireCurrentCustomer(email);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        requireOwner(review, customer);
        reviewRepository.delete(review);
    }

    private ReviewSummary buildPublishedSummary(Long productId) {
        long count = reviewRepository.countByProductIdAndModerationStatus(
                productId, ReviewStatus.APPROVED);

        if (count == 0) {
            return ReviewSummary.empty();
        }

        Double averageRating = reviewRepository.averageRatingByProductIdAndModerationStatus(
                productId, ReviewStatus.APPROVED);

        return new ReviewSummary(count, averageRating);
    }

    private List<ReviewResponse> buildPublishedReviews(Long productId) {
        return reviewRepository
                .findByProductIdAndModerationStatusOrderByCreatedAtDescIdDesc(
                        productId, ReviewStatus.APPROVED)
                .stream()
                .map(ReviewResponse::from)
                .toList();
    }

    private ReviewResponse findCurrentUserReviewInternal(Long productId, String email) {
        User customer = findActiveCustomer(email);
        if (customer == null) {
            return null;
        }

        return reviewRepository.findByCustomerIdAndProductId(customer.getId(), productId)
                .map(ReviewResponse::from)
                .orElse(null);
    }

    private User findActiveCustomer(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isEnabled() || user.getRole() != Role.CUSTOMER) {
            return null;
        }
        return user;
    }

    private User requireCurrentCustomer(String email) {
        User customer = findActiveCustomer(email);
        if (customer == null) {
            throw new AccessDeniedException("An active customer account is required");
        }
        return customer;
    }

    private Product requireActiveProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void requireOwner(Review review, User customer) {
        if (review.getCustomer() == null
                || !review.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("You can only modify your own review");
        }
    }

    private int requireValidRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        return rating;
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Comment is required");
        }

        String trimmed = comment.trim();
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment must not exceed 1000 characters");
        }
        return trimmed;
    }
}
