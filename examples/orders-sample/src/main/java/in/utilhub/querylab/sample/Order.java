package in.utilhub.querylab.sample;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customer;

    public Order() {}

    public Order(String customer) {
        this.customer = customer;
    }

    public Long getId() { return id; }
    public String getCustomer() { return customer; }
}
