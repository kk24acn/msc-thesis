package uk.ac.herts.orchestrator.exception;

public class TransactionSigningException extends RuntimeException {
    public TransactionSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
