package mn.icode.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminOrderDetail(Long id, Instant createdAt, String status,
                               String customerEmail, String customerName,
                               String shipTitle, String shipCity, String shipDistrict,
                               String shipAddressLine, String shipPhone,
                               BigDecimal subtotal, BigDecimal total,
                               List<OrderItemSummary> items) {
}
