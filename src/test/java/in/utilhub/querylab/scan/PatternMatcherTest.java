package in.utilhub.querylab.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatternMatcherTest {

    @Test
    void getXxxIsRecognised() {
        assertEquals("name", PatternMatcher.beanPropertyFromGetter("getName"));
        assertEquals("orderItems", PatternMatcher.beanPropertyFromGetter("getOrderItems"));
    }

    @Test
    void isXxxIsRecognised() {
        assertEquals("active", PatternMatcher.beanPropertyFromGetter("isActive"));
        assertEquals("paid", PatternMatcher.beanPropertyFromGetter("isPaid"));
    }

    @Test
    void hasXxxIsRecognised() {
        assertEquals("children", PatternMatcher.beanPropertyFromGetter("hasChildren"));
    }

    @Test
    void nonGetterIsNotRecognised() {
        assertNull(PatternMatcher.beanPropertyFromGetter("doSomething"));
        assertNull(PatternMatcher.beanPropertyFromGetter("save"));
        assertNull(PatternMatcher.beanPropertyFromGetter("get"));     // no suffix
        assertNull(PatternMatcher.beanPropertyFromGetter("getter"));  // not capitalised after "get"
        assertNull(PatternMatcher.beanPropertyFromGetter("ishadow")); // not capitalised after "is"
    }
}
