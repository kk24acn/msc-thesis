package uk.ac.herts.orchestrator.exception.transaction;

public class TransactionSigningException extends RuntimeException {
    public TransactionSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
