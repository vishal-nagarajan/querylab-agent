package in.utilhub.querylab.sample;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LineItemRepository extends JpaRepository<LineItem, Long> {

    /**
     * Returns just the IDs — does NOT load LineItem entities into the persistence context, so
     * subsequent findById(id) calls actually hit the database (no L1 cache short-circuit).
     */
    @Query("select li.id from LineItem li where li.orderId = :orderId order by li.id")
    List<Long> findIdsByOrderId(@Param("orderId") Long orderId);

    /**
     * Native query that filters on a column without an index. EXPLAIN should reveal
     * a full table scan — the {@code lost_index} rule fires on this in querylab:explain mode.
     */
    @Query(value = "SELECT id, order_id, description FROM line_item WHERE description = ?1",
           nativeQuery = true)
    List<LineItem> findByDescriptionNative(String description);
}
