/**
 * Contract compliance test classes for jwright extensions.
 * <p>
 * This package provides abstract test classes that verify implementations
 * honor the stable contracts defined in jwright-core. Extension developers
 * should extend these classes to ensure their implementations are compliant.
 * </p>
 *
 * <h2>Available Contract Tests</h2>
 * <ul>
 *   <li>{@link ee.jwright.core.contract.AbstractContextExtractorTest} - for ContextExtractor implementations</li>
 *   <li>{@link ee.jwright.core.contract.AbstractCodeWriterTest} - for CodeWriter implementations</li>
 *   <li>{@link ee.jwright.core.contract.AbstractTaskTest} - for Task implementations</li>
 *   <li>{@link ee.jwright.core.contract.AbstractBuildToolTest} - for BuildTool implementations</li>
 *   <li>{@link ee.jwright.core.contract.AbstractLlmClientTest} - for LlmClient implementations</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // In your extension module's test sources
 * class MyExtractorContractTest extends AbstractContextExtractorTest {
 *     @Override
 *     protected ContextExtractor createExtractor() {
 *         return new MyExtractor();
 *     }
 *     // ... implement other abstract methods
 * }
 * }</pre>
 */
package ee.jwright.core.contract;
