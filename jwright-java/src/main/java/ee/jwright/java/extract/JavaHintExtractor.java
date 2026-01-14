package ee.jwright.java.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
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
 * Extracts @JwrightHint annotations from Java test methods.
 * <p>
 * Supports single hints and multiple hints (via @JwrightHints container
 * or repeated @JwrightHint annotations).
 * </p>
 *
 * <h2>Order: 400 (Hints range)</h2>
 */
@Component
@Order(400)
public class JavaHintExtractor implements ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaHintExtractor.class);

    private static final String JWRIGHT_HINT = "JwrightHint";
    private static final String JWRIGHT_HINTS = "JwrightHints";

    @Override
    public String getId() {
        return "java-hint";
    }

    @Override
    public int getOrder() {
        return 400;
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
                log.debug("Method '{}' not found for hint extraction", request.testMethodName());
                return;
            }

            MethodDeclaration method = methodOpt.get();

            // Process all annotations on the method
            for (AnnotationExpr annotation : method.getAnnotations()) {
                String annotationName = annotation.getNameAsString();

                if (JWRIGHT_HINT.equals(annotationName)) {
                    extractSingleHint(annotation, builder);
                } else if (JWRIGHT_HINTS.equals(annotationName)) {
                    extractMultipleHints(annotation, builder);
                }
            }

        } catch (IOException e) {
            log.error("Failed to parse test file for hints: {}", request.testFile(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Java syntax in test file: {}", request.testFile(), e);
        }
    }

    /**
     * Extracts hint from a single @JwrightHint annotation.
     */
    private void extractSingleHint(AnnotationExpr annotation, ExtractionContext.Builder builder) {
        if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
            // @JwrightHint("hint text")
            Expression value = singleMember.getMemberValue();
            extractHintValue(value, builder);
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            // @JwrightHint(value = "hint text")
            for (MemberValuePair pair : normal.getPairs()) {
                if ("value".equals(pair.getNameAsString())) {
                    extractHintValue(pair.getValue(), builder);
                }
            }
        }
    }

    /**
     * Extracts hints from a @JwrightHints container annotation.
     */
    private void extractMultipleHints(AnnotationExpr annotation, ExtractionContext.Builder builder) {
        if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
            // @JwrightHints({@JwrightHint("hint1"), @JwrightHint("hint2")})
            Expression value = singleMember.getMemberValue();
            if (value instanceof ArrayInitializerExpr array) {
                for (Expression element : array.getValues()) {
                    if (element instanceof AnnotationExpr innerAnnotation) {
                        extractSingleHint(innerAnnotation, builder);
                    }
                }
            }
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            // @JwrightHints(value = {...})
            for (MemberValuePair pair : normal.getPairs()) {
                if ("value".equals(pair.getNameAsString()) && pair.getValue() instanceof ArrayInitializerExpr array) {
                    for (Expression element : array.getValues()) {
                        if (element instanceof AnnotationExpr innerAnnotation) {
                            extractSingleHint(innerAnnotation, builder);
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts the hint text from a string literal expression.
     */
    private void extractHintValue(Expression value, ExtractionContext.Builder builder) {
        if (value instanceof StringLiteralExpr stringLiteral) {
            String hint = stringLiteral.getValue();
            builder.addHint(hint);
            log.debug("Extracted hint: {}", hint);
        }
    }
}
