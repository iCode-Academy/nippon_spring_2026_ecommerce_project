package mn.icode.dto;

public class StockAdjustmentRequest {

    private Integer quantity;

    public StockAdjustmentRequest() {
    }

    public StockAdjustmentRequest(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}