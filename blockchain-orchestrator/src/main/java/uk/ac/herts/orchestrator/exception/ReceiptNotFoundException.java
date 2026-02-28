package uk.ac.herts.orchestrator.exception;

import java.util.UUID;

public class ReceiptNotFoundException extends RuntimeException {
    public ReceiptNotFoundException(UUID transactionId) {
        super("Receipt not found " + transactionId);
    }
}
