package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.MethodSignature;
import ee.jwright.core.extract.TypeDefinition;
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
 * Extracts the implementation class structure including fields and methods.
 * <p>
 * This extractor adds the target implementation class as a type definition,
 * providing the LLM with knowledge of available fields and methods in the
 * class being implemented.
 * </p>
 *
 * <h2>Order: 510 (Implementation analysis range, after target method)</h2>
 */
@Component
@Order(510)
public class JavaImplClassExtractor implements ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaImplClassExtractor.class);

    @Override
    public String getId() {
        return "java-impl-class";
    }

    @Override
    public int getOrder() {
        return 510;
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

            // Find the implementation class
            String implClassName = extractClassName(request.implFile().getFileName().toString());
            Optional<ClassOrInterfaceDeclaration> classOpt = cu.getClassByName(implClassName);

            if (classOpt.isEmpty()) {
                log.debug("Implementation class '{}' not found in '{}'", implClassName, request.implFile());
                return;
            }

            ClassOrInterfaceDeclaration classDecl = classOpt.get();
            TypeDefinition typeDef = extractTypeDefinition(implClassName, classDecl);
            builder.addTypeDefinition(typeDef);

            log.debug("Extracted implementation class '{}' with {} fields and {} methods",
                implClassName, typeDef.fields().size(), typeDef.methods().size());

        } catch (IOException e) {
            log.error("Failed to parse impl file: {}", request.implFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in impl file: {}", request.implFile(), e);
        }
    }

    /**
     * Extracts the class name from a file name.
     */
    private String extractClassName(String fileName) {
        // Remove .java extension
        if (fileName.endsWith(".java")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    /**
     * Extracts a type definition from a class declaration.
     */
    private TypeDefinition extractTypeDefinition(String className, ClassOrInterfaceDeclaration classDecl) {
        List<String> fields = new ArrayList<>();
        List<MethodSignature> methods = new ArrayList<>();

        // Extract fields
        for (FieldDeclaration field : classDecl.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                String fieldStr = field.getElementType().asString() + " " + var.getNameAsString();
                fields.add(fieldStr);
            }
        }

        // Extract methods (excluding the target method to avoid confusion)
        for (MethodDeclaration method : classDecl.getMethods()) {
            methods.add(extractMethodSignature(method));
        }

        return new TypeDefinition(className, fields, methods);
    }

    /**
     * Extracts a method signature from a method declaration.
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
