package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.Type;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extracts type definitions referenced in Java test methods.
 * <p>
 * Identifies custom types used in the test (variable declarations, method return types)
 * and extracts their field and method information from source files.
 * </p>
 *
 * <h2>Order: 600 (Type definitions range)</h2>
 */
@Component
@Order(600)
public class JavaTypeDefinitionExtractor implements ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaTypeDefinitionExtractor.class);

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
        "int", "long", "short", "byte", "float", "double", "boolean", "char", "void"
    );

    private static final Set<String> BOXED_TYPES = Set.of(
        "Integer", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Character", "Void"
    );

    private static final Set<String> COMMON_TYPES = Set.of(
        "String", "Object", "Class", "List", "Set", "Map", "Collection",
        "Optional", "Stream", "Iterable", "Comparable"
    );

    @Override
    public String getId() {
        return "java-type-definition";
    }

    @Override
    public int getOrder() {
        return 600;
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
                log.debug("Method '{}' not found for type definition extraction", request.testMethodName());
                return;
            }

            MethodDeclaration method = methodOpt.get();
            Set<String> typeNames = collectTypeNames(method);

            for (String typeName : typeNames) {
                if (shouldExtractType(typeName)) {
                    extractTypeDefinition(typeName, request.sourceRoot(), builder);
                }
            }

        } catch (IOException e) {
            log.error("Failed to parse test file for type definitions: {}", request.testFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in test file: {}", request.testFile(), e);
        }
    }

    /**
     * Collects all type names referenced in the method.
     */
    private Set<String> collectTypeNames(MethodDeclaration method) {
        Set<String> typeNames = new HashSet<>();

        // From variable declarations
        method.findAll(VariableDeclarationExpr.class).forEach(varDecl -> {
            String typeName = varDecl.getElementType().asString();
            typeNames.add(getSimpleTypeName(typeName));
        });

        // From object creations (new Type())
        method.findAll(ObjectCreationExpr.class).forEach(creation -> {
            String typeName = creation.getType().asString();
            typeNames.add(getSimpleTypeName(typeName));
        });

        return typeNames;
    }

    /**
     * Gets the simple type name from a potentially generic type.
     */
    private String getSimpleTypeName(String typeName) {
        // Remove generic parameters: List<String> -> List
        int genericStart = typeName.indexOf('<');
        if (genericStart > 0) {
            return typeName.substring(0, genericStart);
        }
        return typeName;
    }

    /**
     * Determines if a type should be extracted.
     */
    private boolean shouldExtractType(String typeName) {
        return !PRIMITIVE_TYPES.contains(typeName)
            && !BOXED_TYPES.contains(typeName)
            && !COMMON_TYPES.contains(typeName)
            && !typeName.startsWith("java.")
            && !typeName.startsWith("javax.");
    }

    /**
     * Extracts type definition from source file.
     */
    private void extractTypeDefinition(String typeName, Path sourceRoot, ExtractionContext.Builder builder) {
        if (sourceRoot == null) {
            log.debug("No source root provided, skipping type definition extraction for {}", typeName);
            return;
        }

        // Try to find the source file
        Path typeFile = findTypeFile(typeName, sourceRoot);
        if (typeFile == null) {
            log.debug("Source file not found for type: {}", typeName);
            return;
        }

        try {
            CompilationUnit typeCu = JavaParserUtils.parse(typeFile);
            TypeDefinition typeDefinition = extractFromCompilationUnit(typeName, typeCu);
            if (typeDefinition != null) {
                builder.addTypeDefinition(typeDefinition);
                log.debug("Extracted type definition for: {}", typeName);
            }
        } catch (IOException e) {
            log.debug("Failed to parse type file: {}", typeFile, e);
        } catch (IllegalArgumentException e) {
            log.debug("Invalid Java syntax in type file: {}", typeFile, e);
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
     * Extracts type definition from a compilation unit.
     */
    private TypeDefinition extractFromCompilationUnit(String typeName, CompilationUnit cu) {
        // Try class/interface
        Optional<ClassOrInterfaceDeclaration> classOpt = cu.getClassByName(typeName);
        if (classOpt.isPresent()) {
            return extractFromClass(typeName, classOpt.get());
        }

        // Try interface
        Optional<ClassOrInterfaceDeclaration> interfaceOpt = cu.getInterfaceByName(typeName);
        if (interfaceOpt.isPresent()) {
            return extractFromClass(typeName, interfaceOpt.get());
        }

        // Try record
        List<RecordDeclaration> records = cu.findAll(RecordDeclaration.class);
        for (RecordDeclaration record : records) {
            if (record.getNameAsString().equals(typeName)) {
                return extractFromRecord(typeName, record);
            }
        }

        return null;
    }

    /**
     * Extracts type definition from a class or interface.
     */
    private TypeDefinition extractFromClass(String typeName, ClassOrInterfaceDeclaration classDecl) {
        List<String> fields = new ArrayList<>();
        List<MethodSignature> methods = new ArrayList<>();

        // Extract fields
        for (FieldDeclaration field : classDecl.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                String fieldStr = field.getElementType().asString() + " " + var.getNameAsString();
                fields.add(fieldStr);
            }
        }

        // Extract methods
        for (MethodDeclaration method : classDecl.getMethods()) {
            methods.add(extractMethodSignature(method));
        }

        return new TypeDefinition(typeName, fields, methods);
    }

    /**
     * Extracts type definition from a record.
     */
    private TypeDefinition extractFromRecord(String typeName, RecordDeclaration record) {
        List<String> fields = new ArrayList<>();
        List<MethodSignature> methods = new ArrayList<>();

        // Record components become fields
        record.getParameters().forEach(param -> {
            String fieldStr = param.getType().asString() + " " + param.getNameAsString();
            fields.add(fieldStr);
        });

        // Extract explicit methods
        for (MethodDeclaration method : record.getMethods()) {
            methods.add(extractMethodSignature(method));
        }

        return new TypeDefinition(typeName, fields, methods);
    }

    /**
     * Extracts method signature from a method declaration.
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
