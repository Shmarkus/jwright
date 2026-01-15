package ee.jwright.core.build;

import java.nio.file.Path;

/**
 * A pluggable build tool for compiling and running tests.
 * <p>
 * Implementations handle specific build systems like Maven, Gradle, or Bazel.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see CompilationResult
 * @see TestResult
 */
public interface BuildTool {

    /**
     * Returns the unique identifier for this build tool.
     * <p>
     * Used for configuration and logging.
     * Examples: "maven", "gradle", "bazel"
     * </p>
     *
     * @return the build tool identifier
     */
    String getId();

    /**
     * Returns the execution order for this build tool.
     * <p>
     * Lower numbers are preferred. When multiple build tools support a project,
     * the one with the lowest order is used.
     * </p>
     *
     * @return the execution order (lower = preferred)
     */
    int getOrder();

    /**
     * Determines whether this build tool supports the given project.
     * <p>
     * Called to detect the build system. For example, Maven checks for pom.xml,
     * Gradle checks for build.gradle or build.gradle.kts.
     * </p>
     *
     * @param projectDir the project directory
     * @return true if this build tool can handle the project
     */
    boolean supports(Path projectDir);

    /**
     * Compiles the project.
     * <p>
     * Should compile all source files in the project and return the result.
     * On failure, errors should be populated with specific compilation errors.
     * </p>
     *
     * @param projectDir the project directory
     * @return the compilation result
     */
    CompilationResult compile(Path projectDir);

    /**
     * Runs all tests in the specified test class.
     *
     * @param testClass the fully qualified name of the test class
     * @return the test result
     */
    TestResult runTests(String testClass);

    /**
     * Runs a single test method.
     *
     * @param testClass  the fully qualified name of the test class
     * @param testMethod the name of the test method
     * @return the test result
     */
    TestResult runSingleTest(String testClass, String testMethod);
}
