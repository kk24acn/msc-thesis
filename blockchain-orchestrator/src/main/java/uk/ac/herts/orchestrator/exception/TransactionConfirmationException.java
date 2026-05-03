package uk.ac.herts.orchestrator.exception;

public class TransactionConfirmationException extends RuntimeException {
    public TransactionConfirmationException(String message, Throwable cause) {
        super(message, cause);
    }
}
