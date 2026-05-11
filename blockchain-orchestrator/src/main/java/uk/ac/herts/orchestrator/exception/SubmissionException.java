package uk.ac.herts.orchestrator.exception;

public class SubmissionException extends RuntimeException {
    private final int retries;

    public SubmissionException(String message, int retries, Throwable cause) {
        super(message, cause);
        this.retries = retries;
    }

    public int getRetries() {
        return retries;
    }
}
