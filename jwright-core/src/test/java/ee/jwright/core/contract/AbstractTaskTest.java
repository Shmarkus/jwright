package ee.jwright.core.contract;

import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.task.Task;
import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract test class for verifying Task contract compliance.
 * <p>
 * Extension developers should extend this class and implement {@link #createTask()}
 * to verify their task implementation honors the stable contract.
 * </p>
 */
@DisplayName("Task Contract")
public abstract class AbstractTaskTest {

    protected Task task;

    /**
     * Creates the task implementation under test.
     *
     * @return the task to test
     */
    protected abstract Task createTask();

    /**
     * Creates an extraction context for testing.
     *
     * @return a test extraction context
     */
    protected ExtractionContext createTestContext() {
        return ExtractionContext.builder()
            .testClassName("TestClass")
            .testMethodName("testMethod")
            .build();
    }

    /**
     * Creates a pipeline state for testing.
     * <p>
     * Since PipelineState is internal, this uses Object as placeholder.
     * Implementations should return a mock or stub as needed.
     * </p>
     *
     * @return a test pipeline state
     */
    protected Object createTestState() {
        return new Object();
    }

    @BeforeEach
    void setUp() {
        task = createTask();
    }

    @Test
    @DisplayName("getId should return non-null non-empty identifier")
    void getIdShouldReturnNonEmptyIdentifier() {
        assertThat(task.getId())
            .isNotNull()
            .isNotBlank();
    }

    @Test
    @DisplayName("getOrder should return value within convention range")
    void getOrderShouldReturnValueWithinRange() {
        int order = task.getOrder();
        assertThat(order)
            .isPositive()
            .isLessThan(10000);

        // Log order range for documentation
        String range = order < 200 ? "Generation (100-199)"
            : order < 300 ? "Improvement (200-299)"
            : order < 400 ? "Quality (300-399)"
            : order < 500 ? "Formatting (400-499)"
            : "Custom (500+)";
        // Order is in range: {range}
    }

    @Test
    @DisplayName("isRequired should return consistent value")
    void isRequiredShouldReturnConsistentValue() {
        boolean required1 = task.isRequired();
        boolean required2 = task.isRequired();

        assertThat(required1).isEqualTo(required2);
    }

    @Test
    @DisplayName("shouldRun should not throw")
    void shouldRunShouldNotThrow() {
        var context = createTestContext();
        var state = createTestState();

        // Should not throw
        boolean shouldRun = task.shouldRun(context, state);

        // Result should be deterministic for same inputs
        assertThat(task.shouldRun(context, state)).isEqualTo(shouldRun);
    }

    @Test
    @DisplayName("execute should return valid TaskResult")
    void executeShouldReturnValidTaskResult() {
        var context = createTestContext();
        var state = createTestState();

        if (task.shouldRun(context, state)) {
            TaskResult result = task.execute(context, state);

            assertThat(result).isNotNull();
            assertThat(result.taskId()).isEqualTo(task.getId());
            assertThat(result.status()).isIn(
                TaskStatus.SUCCESS,
                TaskStatus.FAILED,
                TaskStatus.SKIPPED,
                TaskStatus.REVERTED
            );
            assertThat(result.attempts()).isNotNegative();
        }
    }
}
