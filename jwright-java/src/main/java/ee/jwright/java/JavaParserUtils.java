package ee.jwright.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility class for parsing Java source files using JavaParser.
 * <p>
 * Provides static methods to parse Java files and find methods within them.
 * This class wraps the JavaParser library to provide a simplified interface
 * for the jwright Java language support module.
 * </p>
 */
public final class JavaParserUtils {

    private static final JavaParser JAVA_PARSER;

    static {
        ParserConfiguration config = new ParserConfiguration();
        // Use JAVA_17 which supports records (available in JavaParser 3.25.x)
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        JAVA_PARSER = new JavaParser(config);
    }

    private JavaParserUtils() {
        // Utility class - no instantiation
    }

    /**
     * Parses a Java source file and returns the AST CompilationUnit.
     *
     * @param file the path to the Java source file
     * @return the parsed CompilationUnit
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the file contains invalid Java syntax
     */
    public static CompilationUnit parse(Path file) throws IOException {
        ParseResult<CompilationUnit> result = JAVA_PARSER.parse(file);

        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            String problems = result.getProblems().stream()
                .map(p -> p.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Unknown parse error");
            throw new IllegalArgumentException("Failed to parse " + file + ": " + problems);
        }

        return result.getResult().get();
    }

    /**
     * Finds a method by name in the given CompilationUnit.
     * <p>
     * Searches all type declarations (classes, interfaces, enums) in the
     * compilation unit for a method with the specified name.
     * </p>
     *
     * @param cu the CompilationUnit to search
     * @param methodName the name of the method to find
     * @return an Optional containing the MethodDeclaration if found, empty otherwise
     */
    public static Optional<MethodDeclaration> findMethod(CompilationUnit cu, String methodName) {
        return cu.findAll(MethodDeclaration.class).stream()
            .filter(method -> method.getNameAsString().equals(methodName))
            .findFirst();
    }
}
