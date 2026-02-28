package uk.ac.herts.orchestrator.repository.dao;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import uk.ac.herts.orchestrator.model.TransactionStatus;
import uk.ac.herts.orchestrator.repository.ServletTransactionRepository;
import uk.ac.herts.orchestrator.repository.entity.Transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// TODO add retries for DB interaction
@Component
@Profile("servlet")
public class ServletTransactionDao {

    private final ServletTransactionRepository repository;

    public ServletTransactionDao(ServletTransactionRepository repository) {
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

    public Transaction markSubmitting(Transaction transaction) {
        transaction.setStatus(TransactionStatus.SUBMITTING);
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

    public Transaction markCompleted(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
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
