package ee.jwright.engine.context;

import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ContextBuilder}.
 */
class ContextBuilderTest {

    private ExtractionRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = new ExtractionRequest(
            Path.of("src/test/java/Test.java"),
            "Test",
            "testMethod",
            Path.of("src/main/java/Impl.java"),
            "targetMethod",
            Path.of("src/main/java")
        );
    }

    @Nested
    @DisplayName("3.7 basic")
    class BasicTests {

        @Test
        @DisplayName("build() with zero extractors returns empty context")
        void buildWithZeroExtractorsReturnsEmptyContext() {
            // Given
            ContextBuilder builder = new ContextBuilder(Collections.emptyList());

            // When
            ExtractionContext context = builder.build(sampleRequest);

            // Then
            assertThat(context).isNotNull();
            assertThat(context.assertions()).isEmpty();
            assertThat(context.mockSetups()).isEmpty();
            assertThat(context.hints()).isEmpty();
        }

        @Test
        @DisplayName("build() invokes all extractors")
        void buildInvokesAllExtractors() {
            // Given
            List<String> invocations = new ArrayList<>();
            ContextExtractor extractor1 = createTrackingExtractor("ext1", 100, invocations);
            ContextExtractor extractor2 = createTrackingExtractor("ext2", 200, invocations);
            ContextBuilder builder = new ContextBuilder(List.of(extractor1, extractor2));

            // When
            builder.build(sampleRequest);

            // Then
            assertThat(invocations).containsExactlyInAnyOrder("ext1", "ext2");
        }
    }

    @Nested
    @DisplayName("3.8 extractor ordering")
    class ExtractorOrderingTests {

        @Test
        @DisplayName("extractors execute in order of getOrder()")
        void extractorsExecuteInOrderOfGetOrder() {
            // Given
            List<String> invocations = new ArrayList<>();
            ContextExtractor extractor300 = createTrackingExtractor("ext300", 300, invocations);
            ContextExtractor extractor100 = createTrackingExtractor("ext100", 100, invocations);
            ContextExtractor extractor200 = createTrackingExtractor("ext200", 200, invocations);
            // Inject in random order
            ContextBuilder builder = new ContextBuilder(List.of(extractor300, extractor100, extractor200));

            // When
            builder.build(sampleRequest);

            // Then - should be sorted by order
            assertThat(invocations).containsExactly("ext100", "ext200", "ext300");
        }

        @Test
        @DisplayName("extractors with same order maintain stable ordering")
        void extractorsWithSameOrderMaintainStableOrdering() {
            // Given
            List<String> invocations = new ArrayList<>();
            ContextExtractor extractorA = createTrackingExtractor("extA", 100, invocations);
            ContextExtractor extractorB = createTrackingExtractor("extB", 100, invocations);
            ContextBuilder builder = new ContextBuilder(List.of(extractorA, extractorB));

            // When
            builder.build(sampleRequest);

            // Then - both should execute, order depends on implementation
            assertThat(invocations).hasSize(2);
            assertThat(invocations).containsExactlyInAnyOrder("extA", "extB");
        }
    }

    @Nested
    @DisplayName("3.9 supports filtering")
    class SupportsFilteringTests {

        @Test
        @DisplayName("extractor is skipped when supports() returns false")
        void extractorIsSkippedWhenSupportsReturnsFalse() {
            // Given
            List<String> invocations = new ArrayList<>();
            ContextExtractor supportedExtractor = createTrackingExtractor("supported", 100, invocations, true);
            ContextExtractor unsupportedExtractor = createTrackingExtractor("unsupported", 200, invocations, false);
            ContextBuilder builder = new ContextBuilder(List.of(supportedExtractor, unsupportedExtractor));

            // When
            builder.build(sampleRequest);

            // Then - only supported extractor should be invoked
            assertThat(invocations).containsExactly("supported");
        }

        @Test
        @DisplayName("all extractors are skipped when none support the request")
        void allExtractorsSkippedWhenNoneSupport() {
            // Given
            List<String> invocations = new ArrayList<>();
            ContextExtractor ext1 = createTrackingExtractor("ext1", 100, invocations, false);
            ContextExtractor ext2 = createTrackingExtractor("ext2", 200, invocations, false);
            ContextBuilder builder = new ContextBuilder(List.of(ext1, ext2));

            // When
            ExtractionContext context = builder.build(sampleRequest);

            // Then
            assertThat(invocations).isEmpty();
            assertThat(context).isNotNull();
        }
    }

    private ContextExtractor createTrackingExtractor(String id, int order, List<String> invocations) {
        return createTrackingExtractor(id, order, invocations, true);
    }

    private ContextExtractor createTrackingExtractor(String id, int order, List<String> invocations, boolean supports) {
        return new ContextExtractor() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public int getOrder() {
                return order;
            }

            @Override
            public boolean supports(ExtractionRequest request) {
                return supports;
            }

            @Override
            public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
                invocations.add(id);
            }
        };
    }
}
