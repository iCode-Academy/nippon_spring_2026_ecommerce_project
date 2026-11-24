package mn.icode.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mn.icode.dto.ReviewSummary;
import mn.icode.model.ReviewStatus;
import mn.icode.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void getPublishedSummaryCountsOnlyApprovedReviewsAndAveragesTheirRatings() {
        when(reviewRepository.countByProductIdAndModerationStatus(1L, ReviewStatus.APPROVED))
                .thenReturn(3L);
        when(reviewRepository.averageRatingByProductIdAndModerationStatus(1L, ReviewStatus.APPROVED))
                .thenReturn(4.0);

        ReviewSummary summary = reviewService.getPublishedSummary(1L);

        assertEquals(3L, summary.publishedReviewCount());
        assertEquals(4.0, summary.averageRating());
        verify(reviewRepository).countByProductIdAndModerationStatus(1L, ReviewStatus.APPROVED);
        verify(reviewRepository).averageRatingByProductIdAndModerationStatus(1L, ReviewStatus.APPROVED);
    }

    @Test
    void getPublishedSummaryReturnsEmptySummaryWithoutQueryingAverage() {
        when(reviewRepository.countByProductIdAndModerationStatus(2L, ReviewStatus.APPROVED))
                .thenReturn(0L);

        ReviewSummary summary = reviewService.getPublishedSummary(2L);

        assertEquals(0L, summary.publishedReviewCount());
        assertNull(summary.averageRating());
        verify(reviewRepository, never())
                .averageRatingByProductIdAndModerationStatus(2L, ReviewStatus.APPROVED);
    }
}
