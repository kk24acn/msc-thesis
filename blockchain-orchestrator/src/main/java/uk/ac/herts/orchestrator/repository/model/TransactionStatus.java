package uk.ac.herts.orchestrator.repository.model;

public enum TransactionStatus {
    NEW,
    SIGNING,
    SIGNED,
    SUBMITTING,
    IN_MEMPOOL,
    CONFIRMED,
    CONFIRMED_SWEEPED,
    STALLED,
    FAILED,
    CRYPTOGRAPHIC_ABORT,
    VERIFICATION_ABORT;
}