package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
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

    @Override
    public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
        try {
            CompilationUnit cu = JavaParserUtils.parse(request.implFile());

            // Find the target method
            Optional<MethodDeclaration> methodOpt = JavaParserUtils.findMethod(cu, request.targetMethodName());

            if (methodOpt.isPresent()) {
                MethodDeclaration method = methodOpt.get();

                // Extract and set method signature
                MethodSignature signature = extractMethodSignature(method);
                builder.targetSignature(signature);

                // Extract current implementation (method body)
                method.getBody().ifPresent(body -> {
                    builder.currentImplementation(body.toString());
                });

                log.debug("Extracted target method signature: {}", signature);
            } else {
                log.warn("Target method '{}' not found in '{}'",
                    request.targetMethodName(), request.implFile());
            }

        } catch (IOException e) {
            log.error("Failed to parse impl file: {}", request.implFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in impl file: {}", request.implFile(), e);
        }
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
