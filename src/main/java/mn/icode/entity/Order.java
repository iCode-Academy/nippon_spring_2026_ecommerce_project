package mn.icode.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import mn.icode.model.OrderStatus;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status = OrderStatus.PENDING;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@Column(nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal subtotal = BigDecimal.ZERO;

	@Column(nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal total = BigDecimal.ZERO;

	@Column(name = "ship_title", updatable = false)
	private String shipTitle;

	@Column(name = "ship_city", nullable = false, updatable = false)
	private String shipCity;

	@Column(name = "ship_district", nullable = false, updatable = false)
	private String shipDistrict;

	@Column(name = "ship_address_line", nullable = false, updatable = false)
	private String shipAddressLine;

	@Column(name = "ship_phone", nullable = false, updatable = false)
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
		ensureMutable();

		if (item == null) {
			throw new IllegalArgumentException("Item is required");
		}

		item.setOrder(this);
		items.add(item);
	}

	public void recalcTotals() {
		ensureMutable();

		if (items.isEmpty()) {
			throw new IllegalStateException(
					"Order must contain at least one item");
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
		return Collections.unmodifiableList(items);
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public void setUser(User user) {
		ensureMutable();
		this.user = user;
	}

	public void setShipTitle(String shipTitle) {
		ensureMutable();
		this.shipTitle = shipTitle;
	}

	public void setShipCity(String shipCity) {
		ensureMutable();
		this.shipCity = shipCity;
	}

	public void setShipDistrict(String shipDistrict) {
		ensureMutable();
		this.shipDistrict = shipDistrict;
	}

	public void setShipAddressLine(String shipAddressLine) {
		ensureMutable();
		this.shipAddressLine = shipAddressLine;
	}

	public void setShipPhone(String shipPhone) {
		ensureMutable();
		this.shipPhone = shipPhone;
	}

	private void ensureMutable() {
		if (id != null) {
			throw new IllegalStateException(
					"Persisted order snapshots cannot be modified");
		}
	}
}
