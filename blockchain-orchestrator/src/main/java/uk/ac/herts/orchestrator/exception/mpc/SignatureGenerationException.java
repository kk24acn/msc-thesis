package uk.ac.herts.orchestrator.exception.mpc;

import java.time.OffsetDateTime;
import java.util.Set;

public class SignatureGenerationException extends RuntimeException {
    private final int retries;
    private final Set<Integer> quarantinedParties;
    private final OffsetDateTime firstFaultAt;

    public SignatureGenerationException(String message, int retries, Throwable cause,
                                        Set<Integer> quarantinedParties) {
        this(message, retries, cause, quarantinedParties, null);
    }

    public SignatureGenerationException(String message, int retries, Throwable cause,
                                        Set<Integer> quarantinedParties, OffsetDateTime firstFaultAt) {
        super(message, cause);
        this.retries = retries;
        this.quarantinedParties = quarantinedParties != null ? Set.copyOf(quarantinedParties) : Set.of();
        this.firstFaultAt = firstFaultAt;
    }

    public int getRetries() {
        return retries;
    }

    public Set<Integer> getQuarantinedParties() {
        return quarantinedParties;
    }

    public OffsetDateTime getFirstFaultAt() {
        return firstFaultAt;
    }
}
