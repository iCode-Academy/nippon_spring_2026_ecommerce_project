package mn.icode.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminOrderSummary(Long id, Instant createdAt, String status,
                                String customerEmail, BigDecimal total) {
}
