package ee.jwright.core.contract;

import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract test class for verifying ContextExtractor contract compliance.
 * <p>
 * Extension developers should extend this class and implement {@link #createExtractor()}
 * to verify their extractor implementation honors the stable contract.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * class MyExtractorContractTest extends AbstractContextExtractorTest {
 *     @Override
 *     protected ContextExtractor createExtractor() {
 *         return new MyExtractor();
 *     }
 *
 *     @Override
 *     protected ExtractionRequest createSupportedRequest() {
 *         return new ExtractionRequest(...);
 *     }
 *
 *     @Override
 *     protected ExtractionRequest createUnsupportedRequest() {
 *         return new ExtractionRequest(...);
 *     }
 * }
 * }</pre>
 */
@DisplayName("ContextExtractor Contract")
public abstract class AbstractContextExtractorTest {

    protected ContextExtractor extractor;

    /**
     * Creates the extractor implementation under test.
     *
     * @return the extractor to test
     */
    protected abstract ContextExtractor createExtractor();

    /**
     * Creates a request that the extractor should support.
     *
     * @return a supported extraction request
     */
    protected abstract ExtractionRequest createSupportedRequest();

    /**
     * Creates a request that the extractor should NOT support.
     *
     * @return an unsupported extraction request
     */
    protected abstract ExtractionRequest createUnsupportedRequest();

    @BeforeEach
    void setUp() {
        extractor = createExtractor();
    }

    @Test
    @DisplayName("getId should return non-null non-empty identifier")
    void getIdShouldReturnNonEmptyIdentifier() {
        assertThat(extractor.getId())
            .isNotNull()
            .isNotBlank();
    }

    @Test
    @DisplayName("getOrder should return positive value within valid range")
    void getOrderShouldReturnPositiveValue() {
        assertThat(extractor.getOrder())
            .isPositive()
            .isLessThan(10000); // Reasonable upper bound
    }

    @Test
    @DisplayName("supports should return true for supported request")
    void supportsShouldReturnTrueForSupportedRequest() {
        var request = createSupportedRequest();
        if (request != null) {
            assertThat(extractor.supports(request)).isTrue();
        }
    }

    @Test
    @DisplayName("supports should return false for unsupported request")
    void supportsShouldReturnFalseForUnsupportedRequest() {
        var request = createUnsupportedRequest();
        if (request != null) {
            assertThat(extractor.supports(request)).isFalse();
        }
    }

    @Test
    @DisplayName("extract should not throw for supported request")
    void extractShouldNotThrowForSupportedRequest() {
        var request = createSupportedRequest();
        if (request != null && extractor.supports(request)) {
            var builder = ExtractionContext.builder();

            // Should not throw
            extractor.extract(request, builder);

            // Should be able to build
            var context = builder.build();
            assertThat(context).isNotNull();
        }
    }
}
