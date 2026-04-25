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
}
