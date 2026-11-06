package mn.icode.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public class ProductImageOrderForm {
    @NotNull(message = "Image IDs are required")
    private List< @NotNull Long> imageIds;

    public List<Long> getImageIds() {
        return imageIds;
    }

    public void setImageIds(List<Long> imageIds) {
        this.imageIds = imageIds;
    }
}
