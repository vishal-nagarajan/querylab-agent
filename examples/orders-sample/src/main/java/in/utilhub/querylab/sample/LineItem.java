package in.utilhub.querylab.sample;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class LineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private String description;

    public LineItem() {}

    public LineItem(Long orderId, String description) {
        this.orderId = orderId;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getDescription() { return description; }
}
