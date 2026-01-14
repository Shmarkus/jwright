package ee.jwright.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides a hint to jwright about how to implement the test.
 * <p>
 * Place this annotation on test methods to give guidance to the AI model
 * about the expected implementation approach.
 * </p>
 *
 * <h2>Example usage:</h2>
 * <pre>{@code
 * @Test
 * @JwrightHint("Use StringBuilder for concatenation")
 * void shouldConcatenateStrings() {
 *     // test code
 * }
 * }</pre>
 *
 * <p>Multiple hints can be provided:</p>
 * <pre>{@code
 * @Test
 * @JwrightHint("Use recursion")
 * @JwrightHint("Base case is n <= 1")
 * void shouldCalculateFactorial() {
 *     // test code
 * }
 * }</pre>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This annotation is part of the stable API and will not change in backwards-incompatible ways.</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(JwrightHints.class)
public @interface JwrightHint {

    /**
     * The hint text to provide to the AI model.
     *
     * @return the hint text
     */
    String value();
}
