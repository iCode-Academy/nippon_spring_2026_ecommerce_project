package mn.icode.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Customer-supplied review content for CAP-31 create and update requests.
 * The rating bounds and comment length mirror the CAP-07 {@code Review}
 * entity and database constraints.
 */
public record ReviewRequest(
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @NotBlank(message = "Comment is required")
        @Size(max = 1000, message = "Comment must not exceed 1000 characters")
        String comment) {
}
