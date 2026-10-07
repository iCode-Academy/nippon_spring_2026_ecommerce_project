package mn.icode.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name="reviews", uniqueConstraints= {
    @UniqueConstraint(name="uk_review_customer_product", columnNames= {"customer_id", "product_id"})
    } 
)

public class Review {
    
    @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private Integer rating;

    @Column(nullable=false)
    private String comment;

    @ManyToOne()
    private User customer;

    @ManyToOne()
    private Product product;
}
