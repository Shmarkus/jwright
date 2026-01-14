package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.MockSetup;
import ee.jwright.core.extract.VerifyStatement;
import ee.jwright.java.JavaParserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Extracts Mockito mock configurations and verifications from Java test files.
 * <p>
 * Supports:
 * <ul>
 *   <li>when(mock.method()).thenReturn(value) - mock setup</li>
 *   <li>verify(mock).method() - verification statements</li>
 *   <li>verify(mock, times(n)).method() - verification with times</li>
 *   <li>verify(mock, never()).method() - verification with never</li>
 * </ul>
 * </p>
 *
 * <h2>Order: 300 (Mock frameworks range)</h2>
 */
@Component
@Order(300)
public class JavaMockitoExtractor implements ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaMockitoExtractor.class);

    @Override
    public String getId() {
        return "java-mockito";
    }

    @Override
    public int getOrder() {
        return 300;
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
                log.debug("Method '{}' not found for Mockito extraction", request.testMethodName());
                return;
            }

            MethodDeclaration method = methodOpt.get();
            List<MethodCallExpr> methodCalls = method.findAll(MethodCallExpr.class);

            for (MethodCallExpr call : methodCalls) {
                String methodName = call.getNameAsString();

                if ("when".equals(methodName)) {
                    extractWhenThenReturn(call, builder);
                } else if ("verify".equals(methodName)) {
                    extractVerify(call, builder);
                }
            }

        } catch (IOException e) {
            log.error("Failed to parse test file for Mockito: {}", request.testFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in test file: {}", request.testFile(), e);
        }
    }

    /**
     * Extracts when(mock.method()).thenReturn(value) patterns.
     */
    private void extractWhenThenReturn(MethodCallExpr whenCall, ExtractionContext.Builder builder) {
        // when(mock.method()) - the argument is a method call on the mock
        if (whenCall.getArguments().isEmpty()) {
            return;
        }

        Expression whenArg = whenCall.getArguments().get(0);
        if (!(whenArg instanceof MethodCallExpr mockMethodCall)) {
            return;
        }

        // Extract mock object and method from when(mock.method())
        String mockObject = mockMethodCall.getScope()
            .map(Expression::toString)
            .orElse("unknown");
        String method = formatMethodCall(mockMethodCall);

        // Find the thenReturn call - it should be the parent of when()
        // Pattern: when(...).thenReturn(...)
        Optional<MethodCallExpr> thenReturnOpt = whenCall.getParentNode()
            .filter(MethodCallExpr.class::isInstance)
            .map(MethodCallExpr.class::cast)
            .filter(call -> "thenReturn".equals(call.getNameAsString()));

        if (thenReturnOpt.isEmpty()) {
            log.debug("No thenReturn found for when() call");
            return;
        }

        MethodCallExpr thenReturn = thenReturnOpt.get();
        String returnValue = thenReturn.getArguments().isEmpty()
            ? null
            : thenReturn.getArguments().get(0).toString();

        builder.addMockSetup(new MockSetup(mockObject, method, returnValue));
        log.debug("Extracted mock setup: when({}.{}).thenReturn({})", mockObject, method, returnValue);
    }

    /**
     * Extracts verify(mock).method() and verify(mock, times/never).method() patterns.
     */
    private void extractVerify(MethodCallExpr verifyCall, ExtractionContext.Builder builder) {
        if (verifyCall.getArguments().isEmpty()) {
            return;
        }

        // Extract mock object and times from verify()
        String mockObject = verifyCall.getArguments().get(0).toString();
        String times = "1"; // default

        if (verifyCall.getArguments().size() >= 2) {
            times = verifyCall.getArguments().get(1).toString();
        }

        // The verified method is called on the result of verify()
        // Pattern: verify(mock).method() - verify(mock) is the scope of method()
        Optional<MethodCallExpr> verifiedMethodOpt = verifyCall.getParentNode()
            .filter(MethodCallExpr.class::isInstance)
            .map(MethodCallExpr.class::cast);

        if (verifiedMethodOpt.isEmpty()) {
            log.debug("No method call found after verify()");
            return;
        }

        MethodCallExpr verifiedMethod = verifiedMethodOpt.get();
        String method = formatMethodCall(verifiedMethod);

        builder.addVerifyStatement(new VerifyStatement(mockObject, method, times));
        log.debug("Extracted verify: verify({}, {}).{}", mockObject, times, method);
    }

    private String formatMethodCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        if (call.getArguments().isEmpty()) {
            return name + "()";
        } else {
            return name + "(" + call.getArguments().stream()
                .map(Expression::toString)
                .reduce((a, b) -> a + ", " + b)
                .orElse("") + ")";
        }
    }
}
