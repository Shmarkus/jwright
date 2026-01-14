package ee.jwright.java.write;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves and adds missing imports to a CompilationUnit.
 * <p>
 * This utility analyzes types used in the code and adds necessary imports
 * from a predefined set of common Java types and libraries.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve.</p>
 */
public class JavaImportResolver {

    private static final Logger log = LoggerFactory.getLogger(JavaImportResolver.class);

    /**
     * Common type mappings: simple name -> fully qualified name.
     */
    private static final Map<String, String> COMMON_TYPES = new HashMap<>();

    static {
        // java.util
        COMMON_TYPES.put("List", "java.util.List");
        COMMON_TYPES.put("ArrayList", "java.util.ArrayList");
        COMMON_TYPES.put("LinkedList", "java.util.LinkedList");
        COMMON_TYPES.put("Set", "java.util.Set");
        COMMON_TYPES.put("HashSet", "java.util.HashSet");
        COMMON_TYPES.put("TreeSet", "java.util.TreeSet");
        COMMON_TYPES.put("Map", "java.util.Map");
        COMMON_TYPES.put("HashMap", "java.util.HashMap");
        COMMON_TYPES.put("TreeMap", "java.util.TreeMap");
        COMMON_TYPES.put("LinkedHashMap", "java.util.LinkedHashMap");
        COMMON_TYPES.put("Queue", "java.util.Queue");
        COMMON_TYPES.put("Deque", "java.util.Deque");
        COMMON_TYPES.put("Stack", "java.util.Stack");
        COMMON_TYPES.put("Collection", "java.util.Collection");
        COMMON_TYPES.put("Collections", "java.util.Collections");
        COMMON_TYPES.put("Arrays", "java.util.Arrays");
        COMMON_TYPES.put("Optional", "java.util.Optional");
        COMMON_TYPES.put("Objects", "java.util.Objects");
        COMMON_TYPES.put("Comparator", "java.util.Comparator");
        COMMON_TYPES.put("Iterator", "java.util.Iterator");
        COMMON_TYPES.put("Random", "java.util.Random");
        COMMON_TYPES.put("UUID", "java.util.UUID");
        COMMON_TYPES.put("Date", "java.util.Date");
        COMMON_TYPES.put("Calendar", "java.util.Calendar");

        // java.util.stream
        COMMON_TYPES.put("Stream", "java.util.stream.Stream");
        COMMON_TYPES.put("Collectors", "java.util.stream.Collectors");
        COMMON_TYPES.put("IntStream", "java.util.stream.IntStream");

        // java.util.function
        COMMON_TYPES.put("Function", "java.util.function.Function");
        COMMON_TYPES.put("Predicate", "java.util.function.Predicate");
        COMMON_TYPES.put("Consumer", "java.util.function.Consumer");
        COMMON_TYPES.put("Supplier", "java.util.function.Supplier");
        COMMON_TYPES.put("BiFunction", "java.util.function.BiFunction");

        // java.time
        COMMON_TYPES.put("LocalDate", "java.time.LocalDate");
        COMMON_TYPES.put("LocalTime", "java.time.LocalTime");
        COMMON_TYPES.put("LocalDateTime", "java.time.LocalDateTime");
        COMMON_TYPES.put("Instant", "java.time.Instant");
        COMMON_TYPES.put("Duration", "java.time.Duration");
        COMMON_TYPES.put("Period", "java.time.Period");
        COMMON_TYPES.put("ZonedDateTime", "java.time.ZonedDateTime");

        // java.io
        COMMON_TYPES.put("File", "java.io.File");
        COMMON_TYPES.put("IOException", "java.io.IOException");
        COMMON_TYPES.put("InputStream", "java.io.InputStream");
        COMMON_TYPES.put("OutputStream", "java.io.OutputStream");
        COMMON_TYPES.put("Reader", "java.io.Reader");
        COMMON_TYPES.put("Writer", "java.io.Writer");
        COMMON_TYPES.put("BufferedReader", "java.io.BufferedReader");
        COMMON_TYPES.put("BufferedWriter", "java.io.BufferedWriter");

        // java.nio
        COMMON_TYPES.put("Path", "java.nio.file.Path");
        COMMON_TYPES.put("Paths", "java.nio.file.Paths");
        COMMON_TYPES.put("Files", "java.nio.file.Files");

        // java.math
        COMMON_TYPES.put("BigDecimal", "java.math.BigDecimal");
        COMMON_TYPES.put("BigInteger", "java.math.BigInteger");

        // java.util.regex
        COMMON_TYPES.put("Pattern", "java.util.regex.Pattern");
        COMMON_TYPES.put("Matcher", "java.util.regex.Matcher");

        // java.util.concurrent
        COMMON_TYPES.put("ExecutorService", "java.util.concurrent.ExecutorService");
        COMMON_TYPES.put("Executors", "java.util.concurrent.Executors");
        COMMON_TYPES.put("Future", "java.util.concurrent.Future");
        COMMON_TYPES.put("CompletableFuture", "java.util.concurrent.CompletableFuture");
        COMMON_TYPES.put("ConcurrentHashMap", "java.util.concurrent.ConcurrentHashMap");
        COMMON_TYPES.put("AtomicInteger", "java.util.concurrent.atomic.AtomicInteger");
        COMMON_TYPES.put("AtomicLong", "java.util.concurrent.atomic.AtomicLong");
        COMMON_TYPES.put("AtomicBoolean", "java.util.concurrent.atomic.AtomicBoolean");
    }

    /**
     * Resolves and adds missing imports to the compilation unit.
     *
     * @param cu the compilation unit to process
     */
    public static void resolveAndAddImports(CompilationUnit cu) {
        // Get existing imports
        Set<String> existingImports = new HashSet<>();
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isAsterisk()) {
                String name = imp.getNameAsString();
                String simpleName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
                existingImports.add(simpleName);
            }
        }

        // Get package name for same-package type detection
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");

        // Find all type references in the compilation unit
        Set<String> referencedTypes = collectReferencedTypes(cu);

        // Resolve missing imports
        for (String typeName : referencedTypes) {
            if (existingImports.contains(typeName)) {
                continue; // Already imported
            }

            if (isBuiltInType(typeName)) {
                continue; // No import needed for primitives/java.lang
            }

            if (isSamePackageType(cu, typeName, packageName)) {
                continue; // No import needed for same package
            }

            // Try to resolve from common types
            String fqn = COMMON_TYPES.get(typeName);
            if (fqn != null) {
                cu.addImport(fqn);
                existingImports.add(typeName);
                log.debug("Added import: {}", fqn);
            }
        }
    }

    /**
     * Collects all type names referenced in the compilation unit.
     */
    private static Set<String> collectReferencedTypes(CompilationUnit cu) {
        Set<String> types = new HashSet<>();

        // From type declarations (class, interface)
        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            String name = type.getNameAsString();
            types.add(getSimpleName(name));
        });

        // From object creations (new X())
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            String name = expr.getType().getNameAsString();
            types.add(getSimpleName(name));
        });

        // From static method calls (Collections.sort, Arrays.asList)
        cu.findAll(MethodCallExpr.class).forEach(expr -> {
            expr.getScope().ifPresent(scope -> {
                if (scope instanceof NameExpr nameExpr) {
                    String name = nameExpr.getNameAsString();
                    // Check if it looks like a class name (starts with uppercase)
                    if (Character.isUpperCase(name.charAt(0))) {
                        types.add(name);
                    }
                }
            });
        });

        return types;
    }

    /**
     * Gets the simple name from a potentially generic type.
     */
    private static String getSimpleName(String typeName) {
        int genericStart = typeName.indexOf('<');
        if (genericStart > 0) {
            return typeName.substring(0, genericStart);
        }
        return typeName;
    }

    /**
     * Checks if a type is a built-in type that doesn't need importing.
     */
    private static boolean isBuiltInType(String typeName) {
        return switch (typeName) {
            case "int", "long", "short", "byte", "float", "double", "boolean", "char", "void",
                 "Integer", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Character", "Void",
                 "String", "Object", "Class", "Enum", "Exception", "RuntimeException",
                 "Throwable", "Error", "System", "Math", "StringBuilder", "StringBuffer" -> true;
            default -> false;
        };
    }

    /**
     * Checks if a type is defined in the same package.
     */
    private static boolean isSamePackageType(CompilationUnit cu, String typeName, String packageName) {
        // Check if the type is defined in this compilation unit
        for (var type : cu.getTypes()) {
            if (type.getNameAsString().equals(typeName)) {
                return true;
            }
        }

        // Check nested types
        for (var type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (type.getNameAsString().equals(typeName)) {
                return true;
            }
        }

        return false;
    }
}
