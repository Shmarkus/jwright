package ee.jwright.core.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for extraction models.
 */
@DisplayName("Extraction Models")
class ExtractionModelsTest {

    @Nested
    @DisplayName("Assertion record")
    class AssertionTests {

        @Test
        @DisplayName("should create assertion with all fields")
        void shouldCreateAssertionWithAllFields() {
            var assertion = new Assertion("assertEquals", "42", "result", "should return 42");

            assertThat(assertion.type()).isEqualTo("assertEquals");
            assertThat(assertion.expected()).isEqualTo("42");
            assertThat(assertion.actual()).isEqualTo("result");
            assertThat(assertion.message()).isEqualTo("should return 42");
        }

        @Test
        @DisplayName("should allow null message")
        void shouldAllowNullMessage() {
            var assertion = new Assertion("assertThat", "true", "isValid()", null);

            assertThat(assertion.message()).isNull();
        }
    }

    @Nested
    @DisplayName("MockSetup record")
    class MockSetupTests {

        @Test
        @DisplayName("should create mock setup with all fields")
        void shouldCreateMockSetupWithAllFields() {
            var mockSetup = new MockSetup("userRepository", "findById(1L)", "Optional.of(user)");

            assertThat(mockSetup.mockObject()).isEqualTo("userRepository");
            assertThat(mockSetup.method()).isEqualTo("findById(1L)");
            assertThat(mockSetup.returnValue()).isEqualTo("Optional.of(user)");
        }
    }

    @Nested
    @DisplayName("VerifyStatement record")
    class VerifyStatementTests {

        @Test
        @DisplayName("should create verify statement with all fields")
        void shouldCreateVerifyStatementWithAllFields() {
            var verify = new VerifyStatement("emailService", "send(email)", "1");

            assertThat(verify.mockObject()).isEqualTo("emailService");
            assertThat(verify.method()).isEqualTo("send(email)");
            assertThat(verify.times()).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("MethodSignature record")
    class MethodSignatureTests {

        @Test
        @DisplayName("should create method signature with parameters")
        void shouldCreateMethodSignatureWithParameters() {
            var signature = new MethodSignature("calculate", "int", List.of("int a", "int b"));

            assertThat(signature.name()).isEqualTo("calculate");
            assertThat(signature.returnType()).isEqualTo("int");
            assertThat(signature.parameters()).containsExactly("int a", "int b");
        }

        @Test
        @DisplayName("should create method signature without parameters")
        void shouldCreateMethodSignatureWithoutParameters() {
            var signature = new MethodSignature("getName", "String", List.of());

            assertThat(signature.parameters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("TypeDefinition record")
    class TypeDefinitionTests {

        @Test
        @DisplayName("should create type definition with fields and methods")
        void shouldCreateTypeDefinitionWithFieldsAndMethods() {
            var methods = List.of(
                new MethodSignature("getName", "String", List.of()),
                new MethodSignature("setName", "void", List.of("String name"))
            );
            var type = new TypeDefinition(
                "com.example.User",
                List.of("private String name", "private int age"),
                methods
            );

            assertThat(type.name()).isEqualTo("com.example.User");
            assertThat(type.fields()).hasSize(2);
            assertThat(type.methods()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("ExtractionContext record")
    class ExtractionContextTests {

        @Test
        @DisplayName("should build context with builder")
        void shouldBuildContextWithBuilder() {
            var context = ExtractionContext.builder()
                .testClassName("CalculatorTest")
                .testMethodName("testAdd")
                .testMethodBody("int result = calc.add(2, 3); assertEquals(5, result);")
                .addAssertion(new Assertion("assertEquals", "5", "result", null))
                .addHint("Use simple addition")
                .targetSignature(new MethodSignature("add", "int", List.of("int a", "int b")))
                .build();

            assertThat(context.testClassName()).isEqualTo("CalculatorTest");
            assertThat(context.testMethodName()).isEqualTo("testAdd");
            assertThat(context.testMethodBody()).contains("assertEquals");
            assertThat(context.assertions()).hasSize(1);
            assertThat(context.hints()).containsExactly("Use simple addition");
            assertThat(context.targetSignature().name()).isEqualTo("add");
        }

        @Test
        @DisplayName("should build context with mocks")
        void shouldBuildContextWithMocks() {
            var context = ExtractionContext.builder()
                .testClassName("UserServiceTest")
                .testMethodName("testCreateUser")
                .addMockSetup(new MockSetup("userRepository", "save(user)", "savedUser"))
                .addVerifyStatement(new VerifyStatement("eventPublisher", "publish(event)", "1"))
                .build();

            assertThat(context.mockSetups()).hasSize(1);
            assertThat(context.mockSetups().get(0).mockObject()).isEqualTo("userRepository");
            assertThat(context.verifyStatements()).hasSize(1);
            assertThat(context.verifyStatements().get(0).mockObject()).isEqualTo("eventPublisher");
        }

        @Test
        @DisplayName("should build context with type definitions")
        void shouldBuildContextWithTypeDefinitions() {
            var typeDef = new TypeDefinition("User", List.of("String name"), List.of());
            var methods = List.of(new MethodSignature("getName", "String", List.of()));

            var context = ExtractionContext.builder()
                .testClassName("Test")
                .testMethodName("test")
                .addTypeDefinition(typeDef)
                .addAvailableMethods("User", methods)
                .build();

            assertThat(context.typeDefinitions()).hasSize(1);
            assertThat(context.availableMethods()).containsKey("User");
            assertThat(context.availableMethods().get("User")).hasSize(1);
        }

        @Test
        @DisplayName("built context should be immutable - assertions list")
        void builtContextShouldBeImmutableAssertions() {
            var context = ExtractionContext.builder()
                .testClassName("Test")
                .testMethodName("test")
                .addAssertion(new Assertion("assertEquals", "1", "result", null))
                .build();

            assertThatThrownBy(() -> context.assertions().add(new Assertion("fail", "", "", null)))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("built context should be immutable - hints list")
        void builtContextShouldBeImmutableHints() {
            var context = ExtractionContext.builder()
                .testClassName("Test")
                .testMethodName("test")
                .addHint("original hint")
                .build();

            assertThatThrownBy(() -> context.hints().add("new hint"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("built context should be immutable - available methods map")
        void builtContextShouldBeImmutableAvailableMethods() {
            var context = ExtractionContext.builder()
                .testClassName("Test")
                .testMethodName("test")
                .addAvailableMethods("Type1", List.of())
                .build();

            assertThatThrownBy(() -> context.availableMethods().put("Type2", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("empty context should have empty collections")
        void emptyContextShouldHaveEmptyCollections() {
            var context = ExtractionContext.builder()
                .testClassName("Test")
                .testMethodName("test")
                .build();

            assertThat(context.assertions()).isEmpty();
            assertThat(context.mockSetups()).isEmpty();
            assertThat(context.verifyStatements()).isEmpty();
            assertThat(context.hints()).isEmpty();
            assertThat(context.typeDefinitions()).isEmpty();
            assertThat(context.availableMethods()).isEmpty();
            assertThat(context.currentImplementation()).isNull();
            assertThat(context.targetSignature()).isNull();
        }

        @Test
        @DisplayName("should set current implementation")
        void shouldSetCurrentImplementation() {
            var context = ExtractionContext.builder()
                .testClassName("Test")
                .testMethodName("test")
                .currentImplementation("return 0;")
                .build();

            assertThat(context.currentImplementation()).isEqualTo("return 0;");
        }
    }
}
