package mn.icode.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO for full order detail view.
 * All order item fields are immutable snapshots captured at purchase time.
 * Editing a Product after an order is placed will NOT alter displayed order snapshots.
 */
public class OrderDetailResponse {

    private Long id;
    private Instant createdAt;
    private String status;
    private String shipTitle;
    private String shipCity;
    private String shipDistrict;
    private String shipAddressLine;
    private String shipPhone;
    private BigDecimal subtotal;
    private BigDecimal total;
    private Integer itemCount;
    private List<OrderItemSummary> items;

    public OrderDetailResponse() {
    }

    public OrderDetailResponse(Long id, Instant createdAt, String status, String shipTitle,
                               String shipCity, String shipDistrict, String shipAddressLine,
                               String shipPhone, BigDecimal subtotal, BigDecimal total,
                               Integer itemCount, List<OrderItemSummary> items) {
        this.id = id;
        this.createdAt = createdAt;
        this.status = status;
        this.shipTitle = shipTitle;
        this.shipCity = shipCity;
        this.shipDistrict = shipDistrict;
        this.shipAddressLine = shipAddressLine;
        this.shipPhone = shipPhone;
        this.subtotal = subtotal;
        this.total = total;
        this.itemCount = itemCount;
        this.items = items;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public String getShipTitle() { return shipTitle; }
    public String getShipCity() { return shipCity; }
    public String getShipDistrict() { return shipDistrict; }
    public String getShipAddressLine() { return shipAddressLine; }
    public String getShipPhone() { return shipPhone; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public Integer getItemCount() { return itemCount; }
    public List<OrderItemSummary> getItems() { return items; }
}