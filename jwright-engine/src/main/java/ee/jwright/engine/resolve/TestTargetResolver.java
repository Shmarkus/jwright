package ee.jwright.engine.resolve;

import ee.jwright.core.exception.JwrightException;

import java.nio.file.Path;

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
        String fullyQualifiedTestClass = parts[0];
        String testMethodName = parts[1];

        // Extract class names
        String testClassName = extractSimpleClassName(fullyQualifiedTestClass);
        String implClassName = deriveImplClassName(testClassName);

        // Convert to file paths
        String testClassPath = fullyQualifiedTestClass.replace(".", "/");
        String implClassPath = testClassPath.replace(testClassName, implClassName);

        // Build file paths
        Path testFile = projectDir.resolve("src/test/java").resolve(testClassPath + ".java");
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
