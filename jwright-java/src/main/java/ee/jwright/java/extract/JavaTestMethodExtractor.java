package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.java.JavaParserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Extracts test method structure from Java test files.
 * <p>
 * Extracts the test class name, test method name, and test method body
 * from JUnit test files. This is typically the first extractor to run
 * as it provides the basic test structure.
 * </p>
 *
 * <h2>Order: 100 (Test structure range)</h2>
 */
@Component
@Order(100)
public class JavaTestMethodExtractor implements ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaTestMethodExtractor.class);

    @Override
    public String getId() {
        return "java-test-method";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean supports(ExtractionRequest request) {
        return request.testFile().toString().endsWith(".java");
    }

    @Override
    public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
        try {
            CompilationUnit cu = JavaParserUtils.parse(request.testFile());

            // Set the test class name from the request
            builder.testClassName(request.testClassName());

            // Find the test method
            Optional<MethodDeclaration> methodOpt = JavaParserUtils.findMethod(cu, request.testMethodName());

            if (methodOpt.isPresent()) {
                MethodDeclaration method = methodOpt.get();
                builder.testMethodName(method.getNameAsString());

                // Extract method body
                method.getBody().ifPresent(body -> {
                    builder.testMethodBody(body.toString());
                });

                log.debug("Extracted test method '{}' from class '{}'",
                    method.getNameAsString(), request.testClassName());
            } else {
                log.warn("Test method '{}' not found in '{}'",
                    request.testMethodName(), request.testFile());
            }

        } catch (IOException e) {
            log.error("Failed to parse test file: {}", request.testFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in test file: {}", request.testFile(), e);
        }
    }
}
