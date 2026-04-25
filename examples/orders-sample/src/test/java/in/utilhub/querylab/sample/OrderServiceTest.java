package in.utilhub.querylab.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderServiceTest {

    @Autowired private OrderRepository orderRepo;
    @Autowired private LineItemRepository lineItemRepo;
    @Autowired private OrderService service;

    @Test
    void lineItems_planted_n_plus_one() {
        Order order = orderRepo.save(new Order("acme"));
        for (int i = 0; i < 25; i++) {
            lineItemRepo.save(new LineItem(order.getId(), "widget " + i));
        }

        var descriptions = service.lineItems(order.getId());

        assertThat(descriptions).hasSize(25);
        // The agent should now have recorded ~26 SELECTs for this test method:
        // 1 findByOrderId + 25 findById. The rule fires for findById since it >5.
    }
}
