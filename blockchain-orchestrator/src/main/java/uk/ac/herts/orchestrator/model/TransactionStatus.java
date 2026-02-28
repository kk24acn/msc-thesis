package uk.ac.herts.orchestrator.model;

import java.util.EnumSet;

public enum TransactionStatus {
    NEW,
    SIGNING,
    SUBMITTING,
    SUBMITTED,
    FAILED,
    COMPLETED;

    public boolean isFinished() {
        return this == FAILED || this == COMPLETED;
    }

    public static EnumSet<TransactionStatus> inFlightEnumSet() {
        return EnumSet.of(
                TransactionStatus.NEW,
                TransactionStatus.SIGNING,
                TransactionStatus.SUBMITTING);
    }
}
