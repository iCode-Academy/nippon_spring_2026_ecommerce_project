package mn.icode.dto;

import jakarta.validation.constraints.NotNull;

public class StockAdjustmentForm {
    @NotNull(message = "Quantity is required")
    private Integer quantity;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
