package uk.ac.herts.orchestrator.repository.dao;

import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import uk.ac.herts.orchestrator.repository.TransactionRepository;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.repository.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.MDC;

@Component
public class TransactionDao {

    private final TransactionRepository repository;

    public TransactionDao(TransactionRepository repository) {
        this.repository = repository;
    }

    public Transaction createTransaction(String toAddress, BigDecimal amountEther) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Transaction newTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .toAddress(toAddress)
                .amountEther(amountEther)
                .status(TransactionStatus.NEW)
                .createdAt(now)
                .updatedAt(now)
                .traceId(MDC.get("traceId"))
                .build();
        return repository.save(newTransaction);
    }

    public Transaction markSigning(Transaction transaction) {
        transaction.setStatus(TransactionStatus.SIGNING);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markSigned(Transaction transaction, String hexPayload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        transaction.setStatus(TransactionStatus.SIGNED);
        transaction.setSignedHexPayload(hexPayload);
        transaction.setSignedAt(now);
        transaction.setUpdatedAt(now);
        return repository.save(transaction);
    }

    public Transaction markSubmitting(Transaction transaction) {
        transaction.setStatus(TransactionStatus.SUBMITTING);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markSubmitted(Transaction transaction, EthSendTransaction transactionResult) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        transaction.setStatus(TransactionStatus.SUBMITTED);
        transaction.setTransactionHash(transactionResult.getTransactionHash());
        transaction.setSubmittedAt(now);
        transaction.setUpdatedAt(now);
        return repository.save(transaction);
    }

    public Transaction markConfirming(Transaction transaction) {
        transaction.setStatus(TransactionStatus.CONFIRMING);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markConfirmed(Transaction transaction, TransactionReceipt transactionReceipt) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        transaction.setStatus(TransactionStatus.CONFIRMED);
        transaction.setConfirmedAt(now);
        transaction.setUpdatedAt(now);
        return repository.save(transaction);
    }

    public Transaction markFailed(Transaction transaction, String errorMessage) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        fresh.setStatus(TransactionStatus.FAILED);
        fresh.setErrorMessage(errorMessage);
        fresh.setFailedAt(now);
        fresh.setUpdatedAt(now);
        return repository.save(fresh);
    }

}
