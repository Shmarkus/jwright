package ee.jwright.engine.pipeline;

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
 * Tests for {@link BackupManager}.
 */
class BackupManagerTest {

    private BackupManager backupManager;

    @BeforeEach
    void setUp() {
        backupManager = new BackupManager();
    }

    @Nested
    @DisplayName("3.1 snapshot()")
    class SnapshotTests {

        @Test
        @DisplayName("should store file content in memory")
        void shouldStoreFileContentInMemory(@TempDir Path tempDir) throws IOException {
            // Given
            Path file = tempDir.resolve("TestClass.java");
            String originalContent = "public class TestClass { }";
            Files.writeString(file, originalContent);

            // When
            backupManager.snapshot(file);

            // Then - verify content is stored by checking snapshot count
            assertThat(backupManager.getSnapshotCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should store multiple snapshots")
        void shouldStoreMultipleSnapshots(@TempDir Path tempDir) throws IOException {
            // Given
            Path file1 = tempDir.resolve("File1.java");
            Path file2 = tempDir.resolve("File2.java");
            Files.writeString(file1, "content1");
            Files.writeString(file2, "content2");

            // When
            backupManager.snapshot(file1);
            backupManager.snapshot(file2);

            // Then
            assertThat(backupManager.getSnapshotCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should store snapshot even if file is modified after")
        void shouldStoreSnapshotEvenIfFileIsModifiedAfter(@TempDir Path tempDir) throws IOException {
            // Given
            Path file = tempDir.resolve("TestClass.java");
            String originalContent = "original content";
            Files.writeString(file, originalContent);
            backupManager.snapshot(file);

            // When - modify the file after snapshot
            Files.writeString(file, "modified content");

            // Then - snapshot count should still be 1
            assertThat(backupManager.getSnapshotCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("3.2 revert()")
    class RevertTests {

        @Test
        @DisplayName("revertLast() should restore most recent snapshot")
        void revertLastShouldRestoreMostRecentSnapshot(@TempDir Path tempDir) throws IOException {
            // Given
            Path file = tempDir.resolve("TestClass.java");
            String originalContent = "original content";
            Files.writeString(file, originalContent);
            backupManager.snapshot(file);
            Files.writeString(file, "modified content");

            // When
            backupManager.revertLast();

            // Then
            assertThat(Files.readString(file)).isEqualTo(originalContent);
            assertThat(backupManager.getSnapshotCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("revertLast() should only restore most recent of multiple snapshots")
        void revertLastShouldOnlyRestoreMostRecent(@TempDir Path tempDir) throws IOException {
            // Given
            Path file1 = tempDir.resolve("File1.java");
            Path file2 = tempDir.resolve("File2.java");
            Files.writeString(file1, "original1");
            Files.writeString(file2, "original2");
            backupManager.snapshot(file1);
            backupManager.snapshot(file2);
            Files.writeString(file1, "modified1");
            Files.writeString(file2, "modified2");

            // When
            backupManager.revertLast();

            // Then - only file2 should be reverted
            assertThat(Files.readString(file2)).isEqualTo("original2");
            assertThat(Files.readString(file1)).isEqualTo("modified1");
            assertThat(backupManager.getSnapshotCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("revertAll() should restore all snapshots")
        void revertAllShouldRestoreAllSnapshots(@TempDir Path tempDir) throws IOException {
            // Given
            Path file1 = tempDir.resolve("File1.java");
            Path file2 = tempDir.resolve("File2.java");
            Files.writeString(file1, "original1");
            Files.writeString(file2, "original2");
            backupManager.snapshot(file1);
            backupManager.snapshot(file2);
            Files.writeString(file1, "modified1");
            Files.writeString(file2, "modified2");

            // When
            backupManager.revertAll();

            // Then - both files should be reverted
            assertThat(Files.readString(file1)).isEqualTo("original1");
            assertThat(Files.readString(file2)).isEqualTo("original2");
            assertThat(backupManager.getSnapshotCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("revertLast() should be no-op when no snapshots exist")
        void revertLastShouldBeNoOpWhenEmpty() {
            // When/Then - should not throw
            backupManager.revertLast();
            assertThat(backupManager.getSnapshotCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("revertAll() should be no-op when no snapshots exist")
        void revertAllShouldBeNoOpWhenEmpty() {
            // When/Then - should not throw
            backupManager.revertAll();
            assertThat(backupManager.getSnapshotCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("3.3 commit()")
    class CommitTests {

        @Test
        @DisplayName("commit() should clear snapshot stack")
        void commitShouldClearSnapshotStack(@TempDir Path tempDir) throws IOException {
            // Given
            Path file = tempDir.resolve("TestClass.java");
            Files.writeString(file, "original content");
            backupManager.snapshot(file);
            Files.writeString(file, "modified content");

            // When
            backupManager.commit();

            // Then
            assertThat(backupManager.getSnapshotCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("commit() should make revert a no-op")
        void commitShouldMakeRevertNoOp(@TempDir Path tempDir) throws IOException {
            // Given
            Path file = tempDir.resolve("TestClass.java");
            Files.writeString(file, "original content");
            backupManager.snapshot(file);
            String modifiedContent = "modified content";
            Files.writeString(file, modifiedContent);
            backupManager.commit();

            // When
            backupManager.revertLast();
            backupManager.revertAll();

            // Then - file should still have modified content
            assertThat(Files.readString(file)).isEqualTo(modifiedContent);
        }

        @Test
        @DisplayName("commit() should clear all snapshots")
        void commitShouldClearAllSnapshots(@TempDir Path tempDir) throws IOException {
            // Given
            Path file1 = tempDir.resolve("File1.java");
            Path file2 = tempDir.resolve("File2.java");
            Files.writeString(file1, "original1");
            Files.writeString(file2, "original2");
            backupManager.snapshot(file1);
            backupManager.snapshot(file2);

            // When
            backupManager.commit();

            // Then
            assertThat(backupManager.getSnapshotCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("commit() should be no-op when no snapshots exist")
        void commitShouldBeNoOpWhenEmpty() {
            // When/Then - should not throw
            backupManager.commit();
            assertThat(backupManager.getSnapshotCount()).isEqualTo(0);
        }
    }
}
