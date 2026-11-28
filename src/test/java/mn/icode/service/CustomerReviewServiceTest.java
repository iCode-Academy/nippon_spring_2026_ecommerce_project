package mn.icode.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import mn.icode.dto.ReviewRequest;
import mn.icode.dto.ReviewResponse;
import mn.icode.entity.Category;
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

@ExtendWith(MockitoExtension.class)
class CustomerReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ReviewService reviewService;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setFirstName("Nora");
        customer.setLastName("Customer");
        customer.setEmail("nora@example.com");
        customer.setPassword("secret");
        customer.setRole(Role.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", 10L);

        Category category = new Category("Stationery");
        product = new Product(category, "Fountain Pen", "Smooth writing",
                new java.math.BigDecimal("12.50"), 5, true);
        ReflectionTestUtils.setField(product, "id", 20L);
    }

    private static ReviewRequest request(Integer rating, String comment) {
        return new ReviewRequest(rating, comment);
    }

    @Test
    void createReview_eligibleCustomer_createsPendingReview() {
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByCustomerIdAndProductId(10L, 20L)).thenReturn(false);
        when(orderRepository.countOrdersForCustomerContainingProductWithStatus(
                10L, 20L, OrderStatus.DELIVERED)).thenReturn(1L);
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = reviewService.createReview(20L, "nora@example.com", request(4, "Great pen"));

        assertEquals(4, response.rating());
        assertEquals("Great pen", response.comment());
        assertEquals(ReviewStatus.PENDING.name(), response.moderationStatus());
        assertEquals(20L, response.productId());
        verify(reviewRepository).saveAndFlush(any(Review.class));
    }

    @Test
    void createReview_ineligibleCustomer_throwsConflict() {
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByCustomerIdAndProductId(10L, 20L)).thenReturn(false);
        when(orderRepository.countOrdersForCustomerContainingProductWithStatus(
                10L, 20L, OrderStatus.DELIVERED)).thenReturn(0L);

        assertThrows(ConflictException.class,
                () -> reviewService.createReview(20L, "nora@example.com", request(4, "Great pen")));
        verify(reviewRepository, never()).saveAndFlush(any(Review.class));
    }

    @Test
    void createReview_duplicateReview_throwsConflict() {
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByCustomerIdAndProductId(10L, 20L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> reviewService.createReview(20L, "nora@example.com", request(4, "Great pen")));
        verify(reviewRepository, never()).saveAndFlush(any(Review.class));
    }

    @Test
    void createReview_invalidRating_throwsIllegalArgument() {
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class,
                () -> reviewService.createReview(20L, "nora@example.com", request(0, "Great pen")));
        verify(reviewRepository, never()).saveAndFlush(any(Review.class));
    }

    @Test
    void updateReview_owner_updatesAndResetsToPending() {
        Review review = new Review(customer, product, 5, "Original");
        ReflectionTestUtils.setField(review, "id", 30L);
        ReflectionTestUtils.setField(review, "moderationStatus", ReviewStatus.APPROVED);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(reviewRepository.findById(30L)).thenReturn(Optional.of(review));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = reviewService.updateReview(30L, "nora@example.com", request(2, "Not so great"));

        assertEquals(2, response.rating());
        assertEquals("Not so great", response.comment());
        assertEquals(ReviewStatus.PENDING.name(), response.moderationStatus());
    }

    @Test
    void updateReview_foreignCustomer_throwsForbidden() {
        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", 99L);
        Review review = new Review(owner, product, 5, "Owner review");
        ReflectionTestUtils.setField(review, "id", 30L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(reviewRepository.findById(30L)).thenReturn(Optional.of(review));

        assertThrows(AccessDeniedException.class,
                () -> reviewService.updateReview(30L, "nora@example.com", request(3, "Hijack")));
        verify(reviewRepository, never()).saveAndFlush(any(Review.class));
    }

    @Test
    void deleteReview_owner_deletesReview() {
        Review review = new Review(customer, product, 5, "Owner review");
        ReflectionTestUtils.setField(review, "id", 30L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(reviewRepository.findById(30L)).thenReturn(Optional.of(review));

        reviewService.deleteReview(30L, "nora@example.com");

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_foreignCustomer_throwsForbidden() {
        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", 99L);
        Review review = new Review(owner, product, 5, "Owner review");
        ReflectionTestUtils.setField(review, "id", 30L);

        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(reviewRepository.findById(30L)).thenReturn(Optional.of(review));

        assertThrows(AccessDeniedException.class,
                () -> reviewService.deleteReview(30L, "nora@example.com"));
        verify(reviewRepository, never()).delete(any(Review.class));
    }

    @Test
    void deleteReview_missingReview_throwsNotFound() {
        when(userRepository.findByEmail("nora@example.com")).thenReturn(Optional.of(customer));
        when(reviewRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.deleteReview(999L, "nora@example.com"));
    }
}
