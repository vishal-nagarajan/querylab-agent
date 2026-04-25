package in.utilhub.querylab.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply on a test method (or business method, for the static scan path) to declare an expected
 * SQL emission shape. Matching emissions are not flagged.
 * <p>
 * The intended use is "I know this test/method emits N queries; that count is the contract."
 * If reality drifts away from {@link #emissions()}, the rule fires; if reality matches, the
 * rule is suppressed. Add {@link #reason()} so the next reader knows why this was approved.
 *
 * <pre>{@code
 * @Test
 * @QuerylabExpect(emissions = 50, fingerprintLike = "findById", reason = "We deliberately fan-out for the demo seeded dataset")
 * void seedsTwentyOrders() { ... }
 * }</pre>
 *
 * Multiple expectations on the same method are supported via the {@link List repeatable container}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(QuerylabExpect.List.class)
public @interface QuerylabExpect {
    /** Maximum allowed emission count for fingerprints matching {@link #fingerprintLike()}. */
    int emissions() default Integer.MAX_VALUE;

    /** Substring or simple-glob to match against the emitted SQL. Empty = match any fingerprint. */
    String fingerprintLike() default "";

    /** Specific rule id to suppress (e.g. "n_plus_one"). Empty = suppress every rule. */
    String rule() default "";

    /** Human-readable rationale. Required by code review, surfaced in the report. */
    String reason() default "";

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        QuerylabExpect[] value();
    }
}
