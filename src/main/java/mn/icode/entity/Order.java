package mn.icode.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import mn.icode.model.OrderStatus;

@Entity
@Table(name = "orders")
public class Order {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status = OrderStatus.PENDING;
	
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal = BigDecimal.ZERO;
	
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total = BigDecimal.ZERO;
	
	@Column(name = "ship_title")
	private String shipTitle;
	
	@Column(name = "ship_city", nullable = false)
	private String shipCity;
	
	@Column(name = "ship_district", nullable = false)
	private String shipDistrict;
	
	@Column(name = "ship_address_line", nullable = false)
	private String shipAddressLine;
	
	@Column(name = "ship_phone", nullable = false)
	private String shipPhone;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	
	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();
	
	public Order() {
		
	}
	
	public void addItem(OrderItem item) {
		if (item == null) {
			throw new IllegalArgumentException("Item is required");
		}
		item.setOrder(this);
		items.add(item);
	}
	
	public void recalcTotals() {
		if (items.isEmpty()) {
			throw new IllegalStateException("Order must contain at least one item");
		}
		subtotal = items
				.stream()
				.map(OrderItem::getLineTotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		total = subtotal;
	}

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}
	
	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public String getShipTitle() {
		return shipTitle;
	}

	public String getShipCity() {
		return shipCity;
	}

	public String getShipDistrict() {
		return shipDistrict;
	}

	public String getShipAddressLine() {
		return shipAddressLine;
	}

	public String getShipPhone() {
		return shipPhone;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public void setShipTitle(String shipTitle) {
		this.shipTitle = shipTitle;
	}

	public void setShipCity(String shipCity) {
		this.shipCity = shipCity;
	}

	public void setShipDistrict(String shipDistrict) {
		this.shipDistrict = shipDistrict;
	}

	public void setShipAddressLine(String shipAddressLine) {
		this.shipAddressLine = shipAddressLine;
	}

	public void setShipPhone(String shipPhone) {
		this.shipPhone = shipPhone;
	}
}
