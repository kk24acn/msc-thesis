package uk.ac.herts.orchestrator.exception.mpc;

import java.util.Set;

public class SignatureGenerationException extends RuntimeException {
    private final int retries;
    private final Set<Integer> quarantinedParties;

    public SignatureGenerationException(String message, int retries, Throwable cause,
                                        Set<Integer> quarantinedParties) {
        super(message, cause);
        this.retries = retries;
        this.quarantinedParties = quarantinedParties != null ? Set.copyOf(quarantinedParties) : Set.of();
    }

    public int getRetries() {
        return retries;
    }

    public Set<Integer> getQuarantinedParties() {
        return quarantinedParties;
    }
}
