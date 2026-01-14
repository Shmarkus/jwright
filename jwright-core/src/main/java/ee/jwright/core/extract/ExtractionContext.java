package ee.jwright.core.extract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable context containing all extracted information from a test.
 * <p>
 * Built by {@link ContextExtractor} implementations using the nested {@link Builder}.
 * Contains test information, assertions, mock setups, type definitions, and more.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param testClassName         name of the test class
 * @param testMethodName        name of the test method
 * @param testMethodBody        full source of the test method body
 * @param assertions            list of assertions found in the test
 * @param mockSetups            list of mock configurations (when/thenReturn)
 * @param verifyStatements      list of mock verifications
 * @param hints                 user-provided hints via annotations
 * @param targetSignature       signature of the method to implement
 * @param currentImplementation current implementation (if any)
 * @param typeDefinitions       type definitions referenced by the test
 * @param availableMethods      available methods on collaborator types
 */
public record ExtractionContext(
    String testClassName,
    String testMethodName,
    String testMethodBody,
    List<Assertion> assertions,
    List<MockSetup> mockSetups,
    List<VerifyStatement> verifyStatements,
    List<String> hints,
    MethodSignature targetSignature,
    String currentImplementation,
    List<TypeDefinition> typeDefinitions,
    Map<String, List<MethodSignature>> availableMethods
) {

    /**
     * Creates a new builder for constructing an ExtractionContext.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for constructing an immutable {@link ExtractionContext}.
     * <p>
     * Used by extractors to accumulate context information before building
     * the final immutable result.
     * </p>
     */
    public static class Builder {
        private String testClassName;
        private String testMethodName;
        private String testMethodBody;
        private final List<Assertion> assertions = new ArrayList<>();
        private final List<MockSetup> mockSetups = new ArrayList<>();
        private final List<VerifyStatement> verifyStatements = new ArrayList<>();
        private final List<String> hints = new ArrayList<>();
        private MethodSignature targetSignature;
        private String currentImplementation;
        private final List<TypeDefinition> typeDefinitions = new ArrayList<>();
        private final Map<String, List<MethodSignature>> availableMethods = new HashMap<>();

        /**
         * Sets the test class name.
         *
         * @param testClassName the test class name
         * @return this builder
         */
        public Builder testClassName(String testClassName) {
            this.testClassName = testClassName;
            return this;
        }

        /**
         * Sets the test method name.
         *
         * @param testMethodName the test method name
         * @return this builder
         */
        public Builder testMethodName(String testMethodName) {
            this.testMethodName = testMethodName;
            return this;
        }

        /**
         * Sets the test method body.
         *
         * @param testMethodBody the test method body source
         * @return this builder
         */
        public Builder testMethodBody(String testMethodBody) {
            this.testMethodBody = testMethodBody;
            return this;
        }

        /**
         * Adds an assertion.
         *
         * @param assertion the assertion to add
         * @return this builder
         */
        public Builder addAssertion(Assertion assertion) {
            this.assertions.add(assertion);
            return this;
        }

        /**
         * Adds a mock setup.
         *
         * @param mockSetup the mock setup to add
         * @return this builder
         */
        public Builder addMockSetup(MockSetup mockSetup) {
            this.mockSetups.add(mockSetup);
            return this;
        }

        /**
         * Adds a verify statement.
         *
         * @param verifyStatement the verify statement to add
         * @return this builder
         */
        public Builder addVerifyStatement(VerifyStatement verifyStatement) {
            this.verifyStatements.add(verifyStatement);
            return this;
        }

        /**
         * Adds a hint.
         *
         * @param hint the hint text
         * @return this builder
         */
        public Builder addHint(String hint) {
            this.hints.add(hint);
            return this;
        }

        /**
         * Sets the target method signature.
         *
         * @param targetSignature the signature of the method to implement
         * @return this builder
         */
        public Builder targetSignature(MethodSignature targetSignature) {
            this.targetSignature = targetSignature;
            return this;
        }

        /**
         * Sets the current implementation.
         *
         * @param currentImplementation the current implementation source
         * @return this builder
         */
        public Builder currentImplementation(String currentImplementation) {
            this.currentImplementation = currentImplementation;
            return this;
        }

        /**
         * Adds a type definition.
         *
         * @param typeDefinition the type definition to add
         * @return this builder
         */
        public Builder addTypeDefinition(TypeDefinition typeDefinition) {
            this.typeDefinitions.add(typeDefinition);
            return this;
        }

        /**
         * Adds available methods for a type.
         *
         * @param typeName the type name
         * @param methods  the list of method signatures
         * @return this builder
         */
        public Builder addAvailableMethods(String typeName, List<MethodSignature> methods) {
            this.availableMethods.put(typeName, new ArrayList<>(methods));
            return this;
        }

        /**
         * Builds the immutable ExtractionContext.
         *
         * @return the built context
         */
        public ExtractionContext build() {
            return new ExtractionContext(
                testClassName,
                testMethodName,
                testMethodBody,
                List.copyOf(assertions),
                List.copyOf(mockSetups),
                List.copyOf(verifyStatements),
                List.copyOf(hints),
                targetSignature,
                currentImplementation,
                List.copyOf(typeDefinitions),
                Map.copyOf(availableMethods)
            );
        }
    }
}
