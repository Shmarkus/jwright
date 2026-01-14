package ee.jwright.core.write;

/**
 * Result of a code write operation.
 * <p>
 * Indicates success or failure, with an optional error message on failure.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param success      true if the write succeeded
 * @param errorMessage error message if the write failed (null on success)
 */
public record WriteResult(
    boolean success,
    String errorMessage
) {

    /**
     * Creates a successful write result.
     *
     * @return a successful result with no error message
     */
    public static WriteResult ok() {
        return new WriteResult(true, null);
    }

    /**
     * Creates a failed write result.
     *
     * @param errorMessage description of what went wrong
     * @return a failed result with the error message
     */
    public static WriteResult failure(String errorMessage) {
        return new WriteResult(false, errorMessage);
    }
}
