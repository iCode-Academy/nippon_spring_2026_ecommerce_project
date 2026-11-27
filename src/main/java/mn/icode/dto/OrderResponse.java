package mn.icode.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO for a paginated list of orders (order history summary).
 * All fields read from stored order/item snapshot entities so that
 * subsequent edits to mutable Product records never alter displayed data.
 */
public class OrderResponse {

    private Long id;
    private Instant createdAt;
    private String status;
    private BigDecimal total;
    private Integer itemCount;
    private List<OrderItemSummary> items;

    public OrderResponse() {
    }

    public OrderResponse(Long id, Instant createdAt, String status, BigDecimal total,
                         Integer itemCount, List<OrderItemSummary> items) {
        this.id = id;
        this.createdAt = createdAt;
        this.status = status;
        this.total = total;
        this.itemCount = itemCount;
        this.items = items;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public Integer getItemCount() { return itemCount; }
    public List<OrderItemSummary> getItems() { return items; }
}