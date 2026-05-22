package uk.ac.herts.orchestrator.exception;

public class SignatureGenerationException extends RuntimeException {
    private final int retries;
    private final boolean verificationFailure;

    public SignatureGenerationException(String message, int retries, Throwable cause, boolean verificationFailure) {
        super(message, cause);
        this.retries = retries;
        this.verificationFailure = verificationFailure;
    }

    public int getRetries() {
        return retries;
    }

    public boolean isVerificationFailure() {
        return verificationFailure;
    }
}
