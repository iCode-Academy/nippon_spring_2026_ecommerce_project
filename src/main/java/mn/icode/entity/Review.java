package mn.icode.entity;

import java.time.LocalDateTime;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mn.icode.model.ReviewStatus;

@Entity
@Table(name="reviews", uniqueConstraints= {
    @UniqueConstraint(name="uq_review_customer_product", columnNames= {"customer_id", "product_id"})
    } 
)

public class Review {
    
    @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Min(1)
    @Max(5)
    @Column(nullable=false)
    private Integer rating;

    @NotBlank 
    @Size(max=1000)
    @Column(nullable=false, length=1000)
    private String comment;

    @ManyToOne(fetch= FetchType.LAZY)
    @JoinColumn(name="customer_id", nullable=false)
    private User customer;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="product_id", nullable=false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name= "moderation_status", nullable=false)
    private ReviewStatus moderationStatus;

    @Column(name="created_at", updatable=false, nullable=false)
    private LocalDateTime createdAt;

    public Review() {}

    

    public Review(User customer, Product product, Integer rating, String comment) {
      
        if(customer == null) {
            throw new IllegalArgumentException("customer cannot be null");
        }

        if(product == null) {
            throw new IllegalArgumentException("product cannot be null");
        }

        if(rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        this.customer = customer;
        this.product = product;
        this.rating = rating;
        this.comment = comment;
        this.moderationStatus = ReviewStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public User getCustomer() {
        return customer;
    }

    public Product getProduct() {
        return product;
    }

    public ReviewStatus getModerationStatus() {
        return moderationStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
   
}
