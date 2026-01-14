package ee.jwright.core.contract;

import ee.jwright.core.write.CodeWriter;
import ee.jwright.core.write.WriteRequest;
import ee.jwright.core.write.WriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract test class for verifying CodeWriter contract compliance.
 * <p>
 * Extension developers should extend this class and implement {@link #createWriter()}
 * to verify their writer implementation honors the stable contract.
 * </p>
 */
@DisplayName("CodeWriter Contract")
public abstract class AbstractCodeWriterTest {

    protected CodeWriter writer;

    /**
     * Creates the writer implementation under test.
     *
     * @return the writer to test
     */
    protected abstract CodeWriter createWriter();

    /**
     * Creates a request that the writer should support.
     *
     * @return a supported write request
     */
    protected abstract WriteRequest createSupportedRequest();

    /**
     * Creates a request that the writer should NOT support.
     *
     * @return an unsupported write request
     */
    protected abstract WriteRequest createUnsupportedRequest();

    @BeforeEach
    void setUp() {
        writer = createWriter();
    }

    @Test
    @DisplayName("getId should return non-null non-empty identifier")
    void getIdShouldReturnNonEmptyIdentifier() {
        assertThat(writer.getId())
            .isNotNull()
            .isNotBlank();
    }

    @Test
    @DisplayName("getOrder should return positive value within valid range")
    void getOrderShouldReturnPositiveValue() {
        assertThat(writer.getOrder())
            .isPositive()
            .isLessThan(10000);
    }

    @Test
    @DisplayName("supports should return true for supported request")
    void supportsShouldReturnTrueForSupportedRequest() {
        var request = createSupportedRequest();
        if (request != null) {
            assertThat(writer.supports(request)).isTrue();
        }
    }

    @Test
    @DisplayName("supports should return false for unsupported request")
    void supportsShouldReturnFalseForUnsupportedRequest() {
        var request = createUnsupportedRequest();
        if (request != null) {
            assertThat(writer.supports(request)).isFalse();
        }
    }

    @Test
    @DisplayName("write should return non-null result")
    void writeShouldReturnNonNullResult() {
        var request = createSupportedRequest();
        if (request != null && writer.supports(request)) {
            WriteResult result = writer.write(request);
            assertThat(result).isNotNull();
        }
    }
}
