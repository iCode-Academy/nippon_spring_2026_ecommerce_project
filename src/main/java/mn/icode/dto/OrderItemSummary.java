package mn.icode.dto;

import java.math.BigDecimal;

/**
 * Immutable snapshot of an order item at purchase time.
 * Fields are read-only; subsequent edits to Product records
 * never alter these displayed values.
 */
public class OrderItemSummary {

    private final Long productId;
    private final String productName;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final BigDecimal lineTotal;

    public OrderItemSummary() {
        this.productId = null;
        this.productName = null;
        this.unitPrice = null;
        this.quantity = 0;
        this.lineTotal = BigDecimal.ZERO;
    }

    public OrderItemSummary(Long productId, String productName, BigDecimal unitPrice,
                            int quantity, BigDecimal lineTotal) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}