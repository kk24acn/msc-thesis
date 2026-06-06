package uk.ac.herts.orchestrator.exception.transaction;

public class TransactionSubmissionException extends RuntimeException {
    public TransactionSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
