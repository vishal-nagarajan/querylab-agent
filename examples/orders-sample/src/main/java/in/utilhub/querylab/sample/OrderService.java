package in.utilhub.querylab.sample;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final LineItemRepository lineItemRepo;

    public OrderService(OrderRepository orderRepo, LineItemRepository lineItemRepo) {
        this.orderRepo = orderRepo;
        this.lineItemRepo = lineItemRepo;
    }

    /**
     * The textbook N+1: 1 query to find all line-item ids for the order, then N queries (one per
     * row) to load each one individually. The querylab agent should flag the per-id findById
     * fingerprint when its emission count exceeds the rule's threshold inside this test method.
     */
    @Transactional(readOnly = true)
    public List<String> lineItems(Long orderId) {
        // 1 query for the IDs (does NOT populate L1 cache for the entities)
        List<Long> ids = lineItemRepo.findIdsByOrderId(orderId);
        // N queries — one per ID. The textbook N+1.
        return ids.stream()
            .map(id -> lineItemRepo.findById(id).orElseThrow().getDescription())
            .collect(Collectors.toList());
    }
}
