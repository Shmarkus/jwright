package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extracts available method signatures from collaborator classes used in tests.
 * <p>
 * Identifies collaborator types (variables declared in the test method) and
 * extracts their available public methods to provide context for code generation.
 * </p>
 *
 * <h2>Order: 700 (Method signatures range)</h2>
 */
@Component
@Order(700)
public class JavaMethodSignatureExtractor implements ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaMethodSignatureExtractor.class);

    private static final Set<String> SKIP_TYPES = Set.of(
        // Primitives and boxed
        "int", "long", "short", "byte", "float", "double", "boolean", "char", "void",
        "Integer", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Character", "Void",
        // Common types
        "String", "Object", "Class"
    );

    @Override
    public String getId() {
        return "java-method-signature";
    }

    @Override
    public int getOrder() {
        return 700;
    }

    @Override
    public boolean supports(ExtractionRequest request) {
        return request.testFile().toString().endsWith(".java");
    }

    @Override
    public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
        try {
            CompilationUnit cu = JavaParserUtils.parse(request.testFile());
            Optional<MethodDeclaration> methodOpt = JavaParserUtils.findMethod(cu, request.testMethodName());

            if (methodOpt.isEmpty()) {
                log.debug("Method '{}' not found for method signature extraction", request.testMethodName());
                return;
            }

            MethodDeclaration method = methodOpt.get();
            Set<String> collaboratorTypes = collectCollaboratorTypes(method);

            for (String typeName : collaboratorTypes) {
                if (!SKIP_TYPES.contains(typeName)) {
                    List<MethodSignature> methods = extractMethodsFromType(typeName, request.sourceRoot());
                    if (!methods.isEmpty()) {
                        builder.addAvailableMethods(typeName, methods);
                        log.debug("Extracted {} methods from collaborator: {}", methods.size(), typeName);
                    }
                }
            }

        } catch (IOException e) {
            log.error("Failed to parse test file for method signatures: {}", request.testFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in test file: {}", request.testFile(), e);
        }
    }

    /**
     * Collects collaborator type names from the test method.
     */
    private Set<String> collectCollaboratorTypes(MethodDeclaration method) {
        Set<String> types = new HashSet<>();

        // From variable declarations
        method.findAll(VariableDeclarationExpr.class).forEach(varDecl -> {
            String typeName = getSimpleTypeName(varDecl.getElementType().asString());
            types.add(typeName);
        });

        // From object creations
        method.findAll(ObjectCreationExpr.class).forEach(creation -> {
            String typeName = getSimpleTypeName(creation.getType().asString());
            types.add(typeName);
        });

        return types;
    }

    /**
     * Gets the simple type name from a potentially generic type.
     */
    private String getSimpleTypeName(String typeName) {
        int genericStart = typeName.indexOf('<');
        if (genericStart > 0) {
            return typeName.substring(0, genericStart);
        }
        return typeName;
    }

    /**
     * Extracts method signatures from a type's source file.
     */
    private List<MethodSignature> extractMethodsFromType(String typeName, Path sourceRoot) {
        if (sourceRoot == null) {
            return List.of();
        }

        Path typeFile = findTypeFile(typeName, sourceRoot);
        if (typeFile == null) {
            return List.of();
        }

        try {
            CompilationUnit typeCu = JavaParserUtils.parse(typeFile);
            return extractMethodsFromCompilationUnit(typeName, typeCu);
        } catch (IOException e) {
            log.debug("Failed to parse type file: {}", typeFile, e);
            return List.of();
        } catch (IllegalArgumentException e) {
            log.debug("Invalid Java syntax in type file: {}", typeFile, e);
            return List.of();
        }
    }

    /**
     * Finds the source file for a type in the source root.
     */
    private Path findTypeFile(String typeName, Path sourceRoot) {
        // Try direct file name match
        Path directPath = sourceRoot.resolve(typeName + ".java");
        if (Files.exists(directPath)) {
            return directPath;
        }

        // Try searching in subdirectories
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                .filter(p -> p.getFileName().toString().equals(typeName + ".java"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            log.debug("Failed to search for type file: {}", typeName, e);
            return null;
        }
    }

    /**
     * Extracts method signatures from a compilation unit.
     */
    private List<MethodSignature> extractMethodsFromCompilationUnit(String typeName, CompilationUnit cu) {
        List<MethodSignature> methods = new ArrayList<>();

        // Try class
        Optional<ClassOrInterfaceDeclaration> classOpt = cu.getClassByName(typeName);
        if (classOpt.isPresent()) {
            for (MethodDeclaration method : classOpt.get().getMethods()) {
                // Only extract public methods
                if (method.isPublic()) {
                    methods.add(extractMethodSignature(method));
                }
            }
        }

        // Try interface
        Optional<ClassOrInterfaceDeclaration> interfaceOpt = cu.getInterfaceByName(typeName);
        if (interfaceOpt.isPresent()) {
            for (MethodDeclaration method : interfaceOpt.get().getMethods()) {
                methods.add(extractMethodSignature(method));
            }
        }

        return methods;
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
