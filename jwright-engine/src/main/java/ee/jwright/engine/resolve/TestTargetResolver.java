package ee.jwright.engine.resolve;

import ee.jwright.core.exception.JwrightException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class TestTargetResolver {

    public record ResolvedTarget(
        Path testFile,
        String testClassName,
        String testMethodName,
        Path implFile,
        String implClassName,
        String implMethodName,
        Path sourceRoot
    ) {}

    public ResolvedTarget resolve(Path projectDir, String target) throws JwrightException {
        validateTargetFormat(target);

        // Parse target (e.g., "GameTest#testAdd" or "org.example.GameTest#testAdd")
        String[] parts = target.split("#");
        String classReference = parts[0];
        String testMethodName = parts[1];

        // Determine if this is a simple class name or fully qualified
        String fullyQualifiedTestClass;
        Path testFile;

        if (isSimpleClassName(classReference)) {
            // Search for the test class in src/test/java/**/<ClassName>.java
            testFile = findTestFile(projectDir, classReference);
            fullyQualifiedTestClass = deriveFullyQualifiedName(projectDir, testFile);
        } else {
            // Fully qualified - use as-is
            fullyQualifiedTestClass = classReference;
            String testClassPath = fullyQualifiedTestClass.replace(".", "/");
            testFile = projectDir.resolve("src/test/java").resolve(testClassPath + ".java");
        }

        // Extract class names
        String testClassName = extractSimpleClassName(fullyQualifiedTestClass);
        String implClassName = deriveImplClassName(testClassName);

        // Convert to file paths
        String testClassPath = fullyQualifiedTestClass.replace(".", "/");
        String implClassPath = testClassPath.replace(testClassName, implClassName);

        // Build file paths
        Path implFile = projectDir.resolve("src/main/java").resolve(implClassPath + ".java");
        Path sourceRoot = projectDir.resolve("src/main/java");

        // Derive method name
        String implMethodName = deriveImplMethodName(testMethodName);

        return new ResolvedTarget(
            testFile,
            testClassName,
            testMethodName,
            implFile,
            implClassName,
            implMethodName,
            sourceRoot
        );
    }

    /**
     * Checks if the class reference is a simple class name (no package qualifier).
     * A simple class name contains no dots.
     */
    private boolean isSimpleClassName(String classReference) {
        return !classReference.contains(".");
    }

    /**
     * Searches for a test file by simple class name in src/test/java.
     * Throws if no matches found or if multiple matches found (ambiguous).
     */
    private Path findTestFile(Path projectDir, String simpleClassName) throws JwrightException {
        Path testSourceRoot = projectDir.resolve("src/test/java");
        String fileName = simpleClassName + ".java";

        if (!Files.exists(testSourceRoot)) {
            throw new JwrightException(
                JwrightException.ErrorCode.NO_TEST_FOUND,
                "Test class '" + simpleClassName + "' not found: test source directory does not exist: " + testSourceRoot
            );
        }

        List<Path> matches;
        try (Stream<Path> paths = Files.walk(testSourceRoot)) {
            matches = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(fileName))
                .toList();
        } catch (IOException e) {
            throw new JwrightException(
                JwrightException.ErrorCode.EXTRACTION_FAILED,
                "Failed to search for test class '" + simpleClassName + "': " + e.getMessage()
            );
        }

        if (matches.isEmpty()) {
            throw new JwrightException(
                JwrightException.ErrorCode.NO_TEST_FOUND,
                "Test class '" + simpleClassName + "' not found in " + testSourceRoot
            );
        }

        if (matches.size() > 1) {
            String locations = matches.stream()
                .map(p -> testSourceRoot.relativize(p).getParent())
                .map(p -> p == null ? "(default package)" : p.toString())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

            throw new JwrightException(
                JwrightException.ErrorCode.CONFIG_INVALID,
                "Ambiguous test class '" + simpleClassName + "': found in multiple locations: " + locations +
                    ". Please specify the fully qualified class name."
            );
        }

        return matches.get(0);
    }

    /**
     * Derives the fully qualified class name from the test file path.
     */
    private String deriveFullyQualifiedName(Path projectDir, Path testFile) {
        Path testSourceRoot = projectDir.resolve("src/test/java");
        Path relativePath = testSourceRoot.relativize(testFile);

        // Remove .java extension and convert path separators to dots
        String pathStr = relativePath.toString();
        String withoutExtension = pathStr.substring(0, pathStr.length() - 5); // Remove ".java"
        return withoutExtension.replace("/", ".").replace("\\", ".");
    }

    private void validateTargetFormat(String target) throws JwrightException {
        if (!target.contains("#")) {
            throw new JwrightException(
                JwrightException.ErrorCode.CONFIG_INVALID,
                "Invalid target format: '" + target + "'. Expected format: ClassName#methodName"
            );
        }
    }

    private String extractSimpleClassName(String fullyQualifiedClassName) {
        return fullyQualifiedClassName.contains(".")
            ? fullyQualifiedClassName.substring(fullyQualifiedClassName.lastIndexOf(".") + 1)
            : fullyQualifiedClassName;
    }

    private String deriveImplClassName(String testClassName) {
        return testClassName.replace("Test", "");
    }

    private String deriveImplMethodName(String testMethodName) {
        // Strip everything after underscore if present (testAdd_returnsSum -> testAdd)
        String methodBase = testMethodName.contains("_")
            ? testMethodName.substring(0, testMethodName.indexOf("_"))
            : testMethodName;

        // Strip "test" prefix if present (testAdd -> add)
        if (methodBase.startsWith("test")) {
            String withoutTest = methodBase.substring(4);
            // Convert first character to lowercase (Add -> add)
            if (!withoutTest.isEmpty()) {
                return Character.toLowerCase(withoutTest.charAt(0)) + withoutTest.substring(1);
            }
        }

        // No "test" prefix, return as-is (addPlayer -> addPlayer)
        return methodBase;
    }
}
