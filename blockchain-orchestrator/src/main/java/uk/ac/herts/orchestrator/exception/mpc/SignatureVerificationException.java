package uk.ac.herts.orchestrator.exception.mpc;

import java.util.Set;

public class SignatureVerificationException extends SignatureGenerationException {

    public SignatureVerificationException(String message, int retries, Throwable cause,
            Set<Integer> quarantinedParties) {
        super(message, retries, cause, quarantinedParties);
    }
}
