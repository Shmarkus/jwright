package ee.jwright.java.write;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.StaticJavaParser;
import ee.jwright.core.write.CodeWriter;
import ee.jwright.core.write.WriteMode;
import ee.jwright.core.write.WriteRequest;
import ee.jwright.core.write.WriteResult;
import ee.jwright.java.JavaParserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

/**
 * Writes method bodies to Java source files.
 * <p>
 * Supports INJECT mode (insert body into empty/stub method) and REPLACE mode
 * (replace entire method body).
 * </p>
 *
 * <h2>Order: 100</h2>
 */
@Component
@Order(100)
public class JavaMethodBodyWriter implements CodeWriter {

    private static final Logger log = LoggerFactory.getLogger(JavaMethodBodyWriter.class);

    @Override
    public String getId() {
        return "java-method-body";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean supports(WriteRequest request) {
        return request.targetFile().toString().endsWith(".java");
    }

    @Override
    public WriteResult write(WriteRequest request) {
        try {
            CompilationUnit cu = JavaParserUtils.parse(request.targetFile());
            Optional<MethodDeclaration> methodOpt = JavaParserUtils.findMethod(cu, request.targetMethodName());

            if (methodOpt.isEmpty()) {
                return WriteResult.failure("Method '" + request.targetMethodName() + "' not found in " + request.targetFile());
            }

            MethodDeclaration method = methodOpt.get();

            switch (request.mode()) {
                case INJECT -> injectMethodBody(method, request.generatedCode());
                case REPLACE -> replaceMethodBody(method, request.generatedCode());
                default -> {
                    return WriteResult.failure("Unsupported write mode: " + request.mode());
                }
            }

            // Resolve and add any missing imports
            JavaImportResolver.resolveAndAddImports(cu);

            // Write the modified AST back to file
            Files.writeString(request.targetFile(), cu.toString());

            log.debug("Successfully wrote method body for '{}' in {}", request.targetMethodName(), request.targetFile());
            return WriteResult.ok();

        } catch (IOException e) {
            log.error("Failed to write to file: {}", request.targetFile(), e);
            return WriteResult.failure("Failed to write to file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax: {}", e.getMessage(), e);
            return WriteResult.failure("Invalid Java syntax: " + e.getMessage());
        }
    }

    /**
     * Injects the body into an empty or stub method.
     */
    private void injectMethodBody(MethodDeclaration method, String generatedCode) {
        BlockStmt newBody = createBlockStmt(generatedCode);
        method.setBody(newBody);
    }

    /**
     * Replaces the entire method body.
     */
    private void replaceMethodBody(MethodDeclaration method, String generatedCode) {
        BlockStmt newBody = createBlockStmt(generatedCode);
        method.setBody(newBody);
    }

    /**
     * Creates a BlockStmt from the generated code.
     */
    private BlockStmt createBlockStmt(String generatedCode) {
        BlockStmt blockStmt = new BlockStmt();

        // Parse each statement and add to block
        // Wrap in a temporary method to parse as statements
        String wrappedCode = "void __temp__() { " + generatedCode + " }";

        try {
            CompilationUnit tempCu = StaticJavaParser.parse("class __Temp__ { " + wrappedCode + " }");
            Optional<MethodDeclaration> tempMethod = tempCu.findFirst(MethodDeclaration.class);

            if (tempMethod.isPresent() && tempMethod.get().getBody().isPresent()) {
                BlockStmt parsedBlock = tempMethod.get().getBody().get();
                for (Statement stmt : parsedBlock.getStatements()) {
                    blockStmt.addStatement(stmt.clone());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse as statements, treating as single statement: {}", e.getMessage());
            // Fallback: try to parse as a single expression/statement
            try {
                Statement stmt = StaticJavaParser.parseStatement(generatedCode);
                blockStmt.addStatement(stmt);
            } catch (Exception e2) {
                log.warn("Failed to parse as single statement: {}", e2.getMessage());
                // Last resort: add as raw statement (may not work)
                blockStmt.addStatement(StaticJavaParser.parseStatement(generatedCode + ";"));
            }
        }

        return blockStmt;
    }
}
