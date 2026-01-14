package ee.jwright.core.extract;

/**
 * A pluggable extractor that extracts context from a test.
 * <p>
 * Extractors are executed in order of {@link #getOrder()}. Each extractor contributes
 * to building the {@link ExtractionContext} by populating different aspects
 * (test structure, assertions, mocks, hints, etc.).
 * </p>
 *
 * <h2>Order Convention</h2>
 * <table>
 *   <tr><th>Range</th><th>Purpose</th></tr>
 *   <tr><td>100-199</td><td>Test structure (method body, class info)</td></tr>
 *   <tr><td>200-299</td><td>Assertions and expectations</td></tr>
 *   <tr><td>300-399</td><td>Mock frameworks</td></tr>
 *   <tr><td>400-499</td><td>Hints and annotations</td></tr>
 *   <tr><td>500-599</td><td>Implementation analysis</td></tr>
 *   <tr><td>600-699</td><td>Type definitions</td></tr>
 *   <tr><td>700-799</td><td>Method signatures</td></tr>
 *   <tr><td>800+</td><td>Custom/third-party</td></tr>
 * </table>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see ExtractionRequest
 * @see ExtractionContext
 */
public interface ContextExtractor {

    /**
     * Returns the unique identifier for this extractor.
     * <p>
     * Used for logging and configuration.
     * Examples: "java-test-method", "java-assertion", "java-mockito"
     * </p>
     *
     * @return the extractor identifier
     */
    String getId();

    /**
     * Returns the execution order for this extractor.
     * <p>
     * Lower numbers execute first. See the order convention in the class documentation.
     * </p>
     *
     * @return the execution order (lower = earlier)
     */
    int getOrder();

    /**
     * Determines whether this extractor supports the given request.
     * <p>
     * Called before extraction to allow language-specific or framework-specific
     * extractors to opt out. For example, a Java extractor would return false
     * for Kotlin test files.
     * </p>
     *
     * @param request the extraction request
     * @return true if this extractor can handle the request
     */
    boolean supports(ExtractionRequest request);

    /**
     * Extracts context from the test and populates the builder.
     * <p>
     * Called only if {@link #supports} returned true. Should extract relevant
     * information and add it to the builder. Multiple extractors may contribute
     * to the same builder.
     * </p>
     *
     * @param request the extraction request
     * @param builder the context builder to populate
     */
    void extract(ExtractionRequest request, ExtractionContext.Builder builder);
}
