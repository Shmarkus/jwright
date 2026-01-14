package ee.jwright.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for multiple {@link JwrightHint} annotations.
 * <p>
 * This annotation is automatically applied when multiple {@link JwrightHint}
 * annotations are placed on the same method.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This annotation is part of the stable API and will not change in backwards-incompatible ways.</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JwrightHints {

    /**
     * The array of JwrightHint annotations.
     *
     * @return the hints
     */
    JwrightHint[] value();
}
