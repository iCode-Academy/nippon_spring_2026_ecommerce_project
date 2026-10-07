package mn.icode.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;
	
	@Column(name = "product_id", nullable = false)
	private Long productId;
	
	@Column(name = "product_name", nullable = false)
	private String productName;
	
	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;
	
	@Column(name = "line_total", nullable = false, precision = 12, scale = 2)
	private BigDecimal lineTotal;
	
	@Column(nullable = false)
	private int quantity;
	
	public OrderItem() {
		
	}
	
	public static OrderItem from(Product p, int qty) {
        if (p == null) throw new IllegalArgumentException("Product is required");
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
 
        OrderItem i = new OrderItem();
        i.productId = p.getId();
        i.productName = p.getName();
        i.unitPrice = p.getPrice();
        i.quantity = qty;
        i.lineTotal = p.getPrice()
                .multiply(BigDecimal.valueOf(qty))
                .setScale(2, RoundingMode.HALF_UP);
        return i;
	}

	void setOrder(Order order) {
		this.order = order;
	}

	public Long getId() {
		return id;
	}

	public Order getOrder() {
		return order;
	}

	public Long getProductId() {
		return productId;
	}

	public String getProductName() {
		return productName;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getLineTotal() {
		return lineTotal;
	}

	public int getQuantity() {
		return quantity;
	}
}
