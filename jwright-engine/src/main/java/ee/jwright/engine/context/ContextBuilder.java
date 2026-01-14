package ee.jwright.engine.context;

import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Builds an {@link ExtractionContext} by executing registered extractors.
 * <p>
 * Extractors are executed in order of their {@link ContextExtractor#getOrder()}.
 * Only extractors that return true for {@link ContextExtractor#supports(ExtractionRequest)}
 * are executed.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
@Component
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);

    private final List<ContextExtractor> extractors;

    /**
     * Creates a new ContextBuilder with the given extractors.
     *
     * @param extractors the list of context extractors (will be sorted by order)
     */
    public ContextBuilder(List<ContextExtractor> extractors) {
        // Sort extractors by order
        this.extractors = extractors.stream()
            .sorted(Comparator.comparingInt(ContextExtractor::getOrder))
            .toList();
    }

    /**
     * Builds an extraction context by running all applicable extractors.
     * <p>
     * Extractors are executed in order. Only extractors where
     * {@link ContextExtractor#supports(ExtractionRequest)} returns true are executed.
     * </p>
     *
     * @param request the extraction request
     * @return the built extraction context
     */
    public ExtractionContext build(ExtractionRequest request) {
        ExtractionContext.Builder builder = ExtractionContext.builder();

        for (ContextExtractor extractor : extractors) {
            if (extractor.supports(request)) {
                log.debug("Running extractor: {}", extractor.getId());
                extractor.extract(request, builder);
            } else {
                log.trace("Skipping extractor: {} (does not support request)", extractor.getId());
            }
        }

        return builder.build();
    }
}
