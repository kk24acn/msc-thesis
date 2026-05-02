package uk.ac.herts.orchestrator.repository.model;

import java.util.EnumSet;

public enum TransactionStatus {
    NEW,
    SIGNING,
    SIGNED,
    SUBMITTING,
    SUBMITTED,
    CONFIRMING,
    CONFIRMED,
    FAILED;

    public boolean isFinished() {
        return this == FAILED || this == CONFIRMED;
    }

    public static EnumSet<TransactionStatus> inFlightEnumSet() {
        return EnumSet.of(
                TransactionStatus.NEW,
                TransactionStatus.SIGNING,
                TransactionStatus.SUBMITTING,
                TransactionStatus.CONFIRMING);
    }
}
