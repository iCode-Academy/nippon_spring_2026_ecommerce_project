package mn.icode.dto;

import java.math.BigDecimal;

import mn.icode.entity.Product;

public record ProductResponse(
    Long id,
    String name,
    String description,
    BigDecimal price,
    Integer stockQuantity,
    boolean active,
    Long categoryId,
    String categoryName
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice(), product.getStockQuantity(), product.isActive(), product.getCategory().getId(), product.getCategory().getName());
    }
 }
