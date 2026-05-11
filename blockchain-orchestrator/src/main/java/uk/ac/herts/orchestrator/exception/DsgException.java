package uk.ac.herts.orchestrator.exception;

public class DsgException extends RuntimeException {
    private final int retries;

    public DsgException(String message, int retries, Throwable cause) {
        super(message, cause);
        this.retries = retries;
    }

    public int getRetries() {
        return retries;
    }
}
