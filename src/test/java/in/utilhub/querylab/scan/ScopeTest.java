package in.utilhub.querylab.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scope.matches drives include/exclude on every class. Glob behaviour is easy to get wrong —
 * lock it down.
 */
class ScopeTest {

    @Test
    void doubleStarMatchesMultipleSegments() {
        assertTrue(Scope.matches("in.utilhub.**", "in.utilhub.payments.OrderService"));
        assertTrue(Scope.matches("in.utilhub.**", "in.utilhub.OrderService"));
    }

    @Test
    void singleStarMatchesOneSegment() {
        assertTrue(Scope.matches("in.utilhub.*.OrderService", "in.utilhub.payments.OrderService"));
        // single-star does NOT cross dots
        assertFalse(Scope.matches("in.utilhub.*.OrderService", "in.utilhub.a.b.OrderService"));
    }

    @Test
    void exactMatch() {
        assertTrue(Scope.matches("in.utilhub.OrderService", "in.utilhub.OrderService"));
        assertFalse(Scope.matches("in.utilhub.OrderService", "in.utilhub.OrderServiceImpl"));
    }

    @Test
    void doubleStarAtEnd() {
        assertTrue(Scope.matches("**.legacy.**", "com.acme.legacy.dao.OldDao"));
        assertTrue(Scope.matches("**.tests.**", "in.utilhub.app.tests.UnitTest"));
    }

    @Test
    void differingPackagesAreNotAccepted() {
        assertFalse(Scope.matches("in.utilhub.payments.**", "in.utilhub.orders.OrderService"));
    }
}
