package ee.jwright.java.extract;

import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.MockSetup;
import ee.jwright.core.extract.VerifyStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavaMockitoExtractor}.
 */
class JavaMockitoExtractorTest {

    @TempDir
    Path tempDir;

    private JavaMockitoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JavaMockitoExtractor();
    }

    @Test
    @DisplayName("should have id 'java-mockito'")
    void shouldHaveCorrectId() {
        assertThat(extractor.getId()).isEqualTo("java-mockito");
    }

    @Test
    @DisplayName("should have order 300 (mock frameworks range)")
    void shouldHaveCorrectOrder() {
        assertThat(extractor.getOrder()).isEqualTo(300);
    }

    @Test
    @DisplayName("should support .java files")
    void shouldSupportJavaFiles() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "class Test {}");
        ExtractionRequest request = createRequest(testFile);

        assertThat(extractor.supports(request)).isTrue();
    }

    @Nested
    @DisplayName("when/thenReturn extraction")
    class WhenThenReturnTests {

        @Test
        @DisplayName("should extract simple when().thenReturn()")
        void shouldExtractSimpleWhenThenReturn() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.mockito.Mockito.*;

                class ServiceTest {
                    @Test
                    void shouldReturnValue() {
                        Repository repo = mock(Repository.class);
                        when(repo.findById(1L)).thenReturn(new Entity());
                        Service service = new Service(repo);
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldReturnValue");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.mockSetups()).hasSize(1);
            MockSetup setup = context.mockSetups().get(0);
            assertThat(setup.mockObject()).isEqualTo("repo");
            assertThat(setup.method()).isEqualTo("findById(1L)");
            assertThat(setup.returnValue()).isEqualTo("new Entity()");
        }

        @Test
        @DisplayName("should extract when().thenReturn() with string return value")
        void shouldExtractWhenThenReturnWithString() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.mockito.Mockito.*;

                class ConfigTest {
                    @Test
                    void shouldReturnConfig() {
                        ConfigService config = mock(ConfigService.class);
                        when(config.getValue("key")).thenReturn("value");
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ConfigTest", "shouldReturnConfig");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.mockSetups()).hasSize(1);
            MockSetup setup = context.mockSetups().get(0);
            assertThat(setup.method()).isEqualTo("getValue(\"key\")");
            assertThat(setup.returnValue()).isEqualTo("\"value\"");
        }

        @Test
        @DisplayName("should extract multiple when().thenReturn() calls")
        void shouldExtractMultipleWhenThenReturn() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.mockito.Mockito.*;

                class ServiceTest {
                    @Test
                    void shouldUseMultipleMocks() {
                        UserRepo userRepo = mock(UserRepo.class);
                        OrderRepo orderRepo = mock(OrderRepo.class);
                        when(userRepo.findById(1L)).thenReturn(user);
                        when(orderRepo.findByUserId(1L)).thenReturn(orders);
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldUseMultipleMocks");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.mockSetups()).hasSize(2);
        }

        @Test
        @DisplayName("should extract when with no-arg method")
        void shouldExtractWhenWithNoArgMethod() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.mockito.Mockito.*;

                class TimeTest {
                    @Test
                    void shouldReturnTime() {
                        Clock clock = mock(Clock.class);
                        when(clock.now()).thenReturn(instant);
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "TimeTest", "shouldReturnTime");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.mockSetups()).hasSize(1);
            MockSetup setup = context.mockSetups().get(0);
            assertThat(setup.method()).isEqualTo("now()");
        }
    }

    @Nested
    @DisplayName("verify extraction")
    class VerifyTests {

        @Test
        @DisplayName("should extract simple verify()")
        void shouldExtractSimpleVerify() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.mockito.Mockito.*;

                class ServiceTest {
                    @Test
                    void shouldCallRepository() {
                        Repository repo = mock(Repository.class);
                        Service service = new Service(repo);
                        service.process();
                        verify(repo).save(any());
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldCallRepository");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.verifyStatements()).hasSize(1);
            VerifyStatement verify = context.verifyStatements().get(0);
            assertThat(verify.mockObject()).isEqualTo("repo");
            assertThat(verify.method()).isEqualTo("save(any())");
            assertThat(verify.times()).isEqualTo("1");
        }

        @Test
        @DisplayName("should extract verify() with times()")
        void shouldExtractVerifyWithTimes() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.mockito.Mockito.*;

                class ServiceTest {
                    @Test
                    void shouldCallTwice() {
                        Logger logger = mock(Logger.class);
                        service.process();
                        verify(logger, times(2)).log(anyString());
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldCallTwice");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.verifyStatements()).hasSize(1);
            VerifyStatement verify = context.verifyStatements().get(0);
            assertThat(verify.times()).isEqualTo("times(2)");
        }

        @Test
        @DisplayName("should extract verify() with never()")
        void shouldExtractVerifyWithNever() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.mockito.Mockito.*;

                class ServiceTest {
                    @Test
                    void shouldNotCallOnError() {
                        Notifier notifier = mock(Notifier.class);
                        service.processWithError();
                        verify(notifier, never()).notify(any());
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldNotCallOnError");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.verifyStatements()).hasSize(1);
            VerifyStatement verify = context.verifyStatements().get(0);
            assertThat(verify.times()).isEqualTo("never()");
        }

        @Test
        @DisplayName("should extract multiple verify() statements")
        void shouldExtractMultipleVerify() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.mockito.Mockito.*;

                class ServiceTest {
                    @Test
                    void shouldCallMultipleMethods() {
                        Repository repo = mock(Repository.class);
                        service.process();
                        verify(repo).find(anyLong());
                        verify(repo).save(any());
                        verify(repo).delete(anyLong());
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldCallMultipleMethods");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.verifyStatements()).hasSize(3);
        }
    }

    private Path createTestFile(String content) throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, content);
        return testFile;
    }

    private ExtractionRequest createRequest(Path testFile) {
        return new ExtractionRequest(testFile, "Test", "test", null, null, tempDir);
    }

    private ExtractionRequest createRequest(Path testFile, String className, String methodName) {
        return new ExtractionRequest(testFile, className, methodName, null, null, tempDir);
    }
}
