package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.MethodSignature;
import ee.jwright.java.JavaParserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Extracts target method signature from implementation files.
 * <p>
 * Reads the implementation file and extracts the target method signature
 * and current implementation to provide context for code generation.
 * </p>
 *
 * <h2>Order: 500 (Implementation analysis range)</h2>
 */
@Component
@Order(500)
public class JavaTargetMethodExtractor implements ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaTargetMethodExtractor.class);

    @Override
    public String getId() {
        return "java-target-method";
    }

    @Override
    public int getOrder() {
        return 500;
    }

    @Override
    public boolean supports(ExtractionRequest request) {
        if (request.implFile() == null) {
            return false;
        }
        return request.implFile().toString().endsWith(".java")
            && Files.exists(request.implFile());
    }

    // Methods that are typically not the target (JUnit, assertions, setup, etc.)
    private static final Set<String> EXCLUDED_METHODS = Set.of(
        "assertEquals", "assertNotEquals", "assertTrue", "assertFalse",
        "assertNull", "assertNotNull", "assertSame", "assertNotSame",
        "assertThrows", "assertThat", "of", "asList", "List.of",
        "when", "thenReturn", "verify", "mock", "spy"
    );

    @Override
    public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
        try {
            CompilationUnit implCu = JavaParserUtils.parse(request.implFile());

            // First, try to detect the actual method from the test body
            // This is more reliable than deriving from the test method name
            String detectedMethod = detectMethodFromTestBody(request, implCu);

            if (detectedMethod != null) {
                Optional<MethodDeclaration> methodOpt = JavaParserUtils.findMethod(implCu, detectedMethod);
                if (methodOpt.isPresent()) {
                    if (!detectedMethod.equals(request.targetMethodName())) {
                        log.debug("Detected actual target method '{}' from test body (derived was '{}')",
                            detectedMethod, request.targetMethodName());
                    }
                    extractAndSetSignature(methodOpt.get(), builder);
                    return;
                }
            }

            // Fall back to the derived target method name
            Optional<MethodDeclaration> methodOpt = JavaParserUtils.findMethod(implCu, request.targetMethodName());

            if (methodOpt.isPresent()) {
                extractAndSetSignature(methodOpt.get(), builder);
                return;
            }

            log.warn("Target method '{}' not found in '{}' and could not detect from test body",
                request.targetMethodName(), request.implFile());

        } catch (IOException e) {
            log.error("Failed to parse impl file: {}", request.implFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in impl file: {}", request.implFile(), e);
        }
    }

    /**
     * Extracts and sets the method signature in the builder.
     */
    private void extractAndSetSignature(MethodDeclaration method, ExtractionContext.Builder builder) {
        MethodSignature signature = extractMethodSignature(method);
        builder.targetSignature(signature);

        method.getBody().ifPresent(body -> {
            builder.currentImplementation(body.toString());
        });

        log.debug("Extracted target method signature: {}", signature);
    }

    /**
     * Analyzes the test body to detect the actual method being called on the impl class.
     * Looks for method calls on variables whose type matches the impl class.
     */
    private String detectMethodFromTestBody(ExtractionRequest request, CompilationUnit implCu) {
        try {
            if (request.testFile() == null || !Files.exists(request.testFile())) {
                return null;
            }

            CompilationUnit testCu = JavaParserUtils.parse(request.testFile());
            Optional<MethodDeclaration> testMethodOpt = JavaParserUtils.findMethod(testCu, request.testMethodName());

            if (testMethodOpt.isEmpty()) {
                return null;
            }

            MethodDeclaration testMethod = testMethodOpt.get();

            // Get all method names that exist in the impl class
            Set<String> implMethods = implCu.findAll(MethodDeclaration.class).stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(java.util.stream.Collectors.toSet());

            // Find method calls in the test that match impl class methods
            List<MethodCallExpr> methodCalls = testMethod.findAll(MethodCallExpr.class);

            for (MethodCallExpr call : methodCalls) {
                String methodName = call.getNameAsString();

                // Skip excluded methods (assertions, setup, etc.)
                if (EXCLUDED_METHODS.contains(methodName)) {
                    continue;
                }

                // If this method exists in the impl class, it's likely our target
                if (implMethods.contains(methodName)) {
                    return methodName;
                }
            }

        } catch (IOException e) {
            log.debug("Failed to analyze test file for method detection: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Extracts a method signature from a method declaration.
     *
     * @param method the method declaration
     * @return the method signature
     */
    private MethodSignature extractMethodSignature(MethodDeclaration method) {
        List<String> parameters = new ArrayList<>();
        method.getParameters().forEach(param ->
            parameters.add(param.getType().asString() + " " + param.getNameAsString())
        );

        return new MethodSignature(
            method.getNameAsString(),
            method.getType().asString(),
            parameters
        );
    }
}
