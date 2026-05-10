package uk.ac.herts.orchestrator.repository.model;

public enum TransactionStatus {
    NEW,
    SIGNING,
    SIGNED,
    SUBMITTING,
    IN_MEMPOOL,
    CONFIRMED,
    STALLED,
    FAILED,
    CRYPTOGRAPHIC_ABORT;
}