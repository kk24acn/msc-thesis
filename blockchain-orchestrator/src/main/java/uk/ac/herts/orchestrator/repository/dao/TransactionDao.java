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

// TODO add retries for DB interaction
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
                .build();
        return repository.save(newTransaction);
    }

    public Transaction markSigning(Transaction transaction) {
        transaction.setStatus(TransactionStatus.SIGNING);
        transaction.setRetryCount(0);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markSigning(Transaction transaction, boolean incrementRetryCount) {
        transaction.setStatus(TransactionStatus.SIGNING);
        if (incrementRetryCount) {
            transaction.incrementRetryCount();
        }
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markSigned(Transaction transaction, String hexPayload) {
        transaction.setStatus(TransactionStatus.SIGNED);
        transaction.setSignedHexPayload(hexPayload);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markSubmitting(Transaction transaction) {
        transaction.setStatus(TransactionStatus.SUBMITTING);
        transaction.setRetryCount(0);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markSubmitting(Transaction transaction, boolean incrementRetryCount) {
        transaction.setStatus(TransactionStatus.SUBMITTING);
        if (incrementRetryCount) {
            transaction.incrementRetryCount();
        }
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markSubmitted(Transaction transaction, EthSendTransaction transactionResult) {
        transaction.setStatus(TransactionStatus.SUBMITTED);
        transaction.setTransactionHash(transactionResult.getTransactionHash());
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markConfirming(Transaction transaction) {
        transaction.setStatus(TransactionStatus.CONFIRMING);
        transaction.setRetryCount(0);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markConfirmed(Transaction transaction, TransactionReceipt transactionReceipt) {
        transaction.setStatus(TransactionStatus.CONFIRMED);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markFailed(Transaction transaction, String errorMessage) {
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setErrorMessage(errorMessage);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

}
