package mn.icode.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ProductForm {

    @NotBlank(message="Procuct name is required")
    @Size(max=200, message="Product name must not exceed 200 characters")
    private String name;

    @Size(max=5000, message="Description must not exceed 5000 characters")
    private String description;

    @NotNull(message="Price is required")
    @DecimalMin(value="0.00", inclusive= false, message="Price must be greater than zero")
    @Digits(integer=10, fraction=2, message="Price must have at most 10 integer digits and 2 decimal places")
    private BigDecimal price;

    @NotNull(message="Stock quantity is required")
    @Min(value=0, message="Stock quantity cannot be negative")
    private Integer stockQuantity;

    @NotNull(message="Category is required")
    private Long categoryId;

    private boolean active = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    


}
