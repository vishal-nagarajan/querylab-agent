package in.utilhub.querylab.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply on a class or method to silently skip it from analysis.
 * <p>
 * Static scan and runtime observation both honor this. Useful for:
 * <ul>
 *   <li>Methods that intentionally fan out queries (batch jobs, admin tooling).</li>
 *   <li>Generated/legacy code where flags would only be noise.</li>
 *   <li>Tests that exist only to seed data and shouldn't be measured.</li>
 * </ul>
 *
 * Pair with a non-empty {@link #reason()} so the choice survives code review.
 *
 * <pre>{@code
 * @Service
 * public class NightlyReportService {
 *
 *     @QuerylabIgnore(reason = "Batch job — N+1 here is intentional, runs once per day")
 *     public void exportAll() { ... }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface QuerylabIgnore {
    /** Why is this acceptable to skip? Surfaced in the report's "skipped" section. */
    String reason() default "";
}
