package mn.icode.dto;

public record ReviewSummary(long publishedReviewCount, Double averageRating) {

    public static ReviewSummary empty() {
        return new ReviewSummary(0, null);
    }
}
