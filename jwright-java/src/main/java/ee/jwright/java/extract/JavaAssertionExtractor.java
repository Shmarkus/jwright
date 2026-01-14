package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import ee.jwright.core.extract.Assertion;
import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.java.JavaParserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Extracts assertions from Java test files.
 * <p>
 * Supports both JUnit assertions (assertEquals, assertTrue, assertFalse, assertNotNull)
 * and AssertJ fluent assertions (assertThat().isEqualTo(), assertThat().isTrue(), etc.).
 * </p>
 *
 * <h2>Order: 200 (Assertions range)</h2>
 */
@Component
@Order(200)
public class JavaAssertionExtractor implements ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaAssertionExtractor.class);

    private static final Set<String> JUNIT_ASSERTIONS = Set.of(
        "assertEquals", "assertNotEquals",
        "assertTrue", "assertFalse",
        "assertNull", "assertNotNull",
        "assertSame", "assertNotSame",
        "assertArrayEquals",
        "assertThrows"
    );

    @Override
    public String getId() {
        return "java-assertion";
    }

    @Override
    public int getOrder() {
        return 200;
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
                log.debug("Method '{}' not found for assertion extraction", request.testMethodName());
                return;
            }

            MethodDeclaration method = methodOpt.get();

            // Find all method calls in the test method
            List<MethodCallExpr> methodCalls = method.findAll(MethodCallExpr.class);

            for (MethodCallExpr call : methodCalls) {
                String methodName = call.getNameAsString();

                if (JUNIT_ASSERTIONS.contains(methodName)) {
                    extractJUnitAssertion(call, builder);
                } else if ("assertThat".equals(methodName)) {
                    extractAssertJAssertion(call, builder);
                }
            }

        } catch (IOException e) {
            log.error("Failed to parse test file for assertions: {}", request.testFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in test file: {}", request.testFile(), e);
        }
    }

    private void extractJUnitAssertion(MethodCallExpr call, ExtractionContext.Builder builder) {
        String methodName = call.getNameAsString();
        List<Expression> args = call.getArguments();

        String expected = null;
        String actual = null;
        String message = null;

        switch (methodName) {
            case "assertEquals", "assertNotEquals" -> {
                if (args.size() >= 2) {
                    expected = args.get(0).toString();
                    actual = args.get(1).toString();
                    if (args.size() >= 3) {
                        message = args.get(2).toString();
                    }
                }
            }
            case "assertTrue", "assertFalse" -> {
                if (args.size() >= 1) {
                    actual = args.get(0).toString();
                    if (args.size() >= 2) {
                        message = args.get(1).toString();
                    }
                }
            }
            case "assertNull", "assertNotNull" -> {
                if (args.size() >= 1) {
                    actual = args.get(0).toString();
                    if (args.size() >= 2) {
                        message = args.get(1).toString();
                    }
                }
            }
            case "assertSame", "assertNotSame" -> {
                if (args.size() >= 2) {
                    expected = args.get(0).toString();
                    actual = args.get(1).toString();
                    if (args.size() >= 3) {
                        message = args.get(2).toString();
                    }
                }
            }
            case "assertArrayEquals" -> {
                if (args.size() >= 2) {
                    expected = args.get(0).toString();
                    actual = args.get(1).toString();
                }
            }
            case "assertThrows" -> {
                if (args.size() >= 2) {
                    expected = args.get(0).toString();
                    actual = args.get(1).toString();
                }
            }
        }

        builder.addAssertion(new Assertion(methodName, expected, actual, message));
        log.debug("Extracted JUnit assertion: {}({}, {})", methodName, expected, actual);
    }

    private void extractAssertJAssertion(MethodCallExpr assertThatCall, ExtractionContext.Builder builder) {
        // assertThat takes the actual value as argument
        if (assertThatCall.getArguments().isEmpty()) {
            return;
        }

        String actual = assertThatCall.getArguments().get(0).toString();
        String expected = extractAssertJExpectation(assertThatCall);

        builder.addAssertion(new Assertion("assertThat", expected, actual, null));
        log.debug("Extracted AssertJ assertion: assertThat({}).{}", actual, expected);
    }

    /**
     * Extracts the expectation from an AssertJ chain.
     * For example, from assertThat(result).isEqualTo(5), extracts "5".
     * For assertThat(result).isNotNull(), extracts "isNotNull()".
     */
    private String extractAssertJExpectation(MethodCallExpr assertThatCall) {
        // Find the parent method call chain
        // assertThat(x).isEqualTo(y) - the assertThat call is the scope of isEqualTo
        Optional<MethodCallExpr> parentCallOpt = assertThatCall.getParentNode()
            .filter(MethodCallExpr.class::isInstance)
            .map(MethodCallExpr.class::cast);

        if (parentCallOpt.isEmpty()) {
            return null;
        }

        // Walk up the chain to find all chained method calls
        StringBuilder chain = new StringBuilder();
        MethodCallExpr current = parentCallOpt.get();

        while (current != null) {
            if (chain.length() > 0) {
                chain.insert(0, ".");
            }
            chain.insert(0, formatMethodCall(current));

            // Check if there's another chained call
            Optional<MethodCallExpr> nextOpt = current.getParentNode()
                .filter(MethodCallExpr.class::isInstance)
                .map(MethodCallExpr.class::cast);

            current = nextOpt.orElse(null);
        }

        return chain.toString();
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
