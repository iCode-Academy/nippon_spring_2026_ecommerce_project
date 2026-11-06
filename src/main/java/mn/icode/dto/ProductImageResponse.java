package mn.icode.dto;

import mn.icode.entity.ProductImage;

public record ProductImageResponse(
    Long id,
    String imageUrl,
    Integer position
) {
    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getImageUrl(), image.getPosition());
    }
    
    
}
