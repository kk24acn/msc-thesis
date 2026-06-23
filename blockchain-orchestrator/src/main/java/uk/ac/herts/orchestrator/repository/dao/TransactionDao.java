package uk.ac.herts.orchestrator.repository.dao;

import org.springframework.stereotype.Component;

import uk.ac.herts.orchestrator.repository.TransactionRepository;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.repository.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionDao {

    private final TransactionRepository repository;

    public TransactionDao(TransactionRepository repository) {
        this.repository = repository;
    }

    public Transaction createTransaction(String fromAddress, String toAddress, BigDecimal amountEther) {
        Transaction newTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .amountEther(amountEther)
                .status(TransactionStatus.NEW)
                .build();
        return repository.save(newTransaction);
    }

    public Transaction markSigning(Transaction transaction, Long nonce) {
        transaction.setStatus(TransactionStatus.SIGNING);
        transaction.setNonce(nonce);
        return repository.save(transaction);
    }

    public Transaction markSigningStarted(Transaction transaction) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        fresh.setSigningStartedAt(transaction.getSigningStartedAt());
        return repository.save(fresh);
    }

    public Transaction markSigned(Transaction transaction, String hexPayload, int signingRetries,
            OffsetDateTime firstFaultAt) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        fresh.setStatus(TransactionStatus.SIGNED);
        fresh.setSignedHexPayload(hexPayload);
        fresh.setSigningRetries(signingRetries);
        fresh.setSigningStartedAt(transaction.getSigningStartedAt());
        fresh.setSignedAt(transaction.getSignedAt());
        recordFirstFault(fresh, firstFaultAt);
        return repository.save(fresh);
    }

    public Transaction markSweeperSigned(Transaction transaction, String hexPayload, int signingRetries,
            OffsetDateTime firstFaultAt) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        Integer sweeperRetriesRem = Objects.requireNonNullElse(fresh.getSweeperSigningRetries(), 0);
        fresh.setStatus(TransactionStatus.SIGNED);
        fresh.setSignedHexPayload(hexPayload);
        fresh.setSweeperSigningRetries(sweeperRetriesRem + signingRetries);
        recordFirstFault(fresh, firstFaultAt);
        return repository.save(fresh);
    }

    public Transaction markSubmitting(Transaction transaction) {
        transaction.setStatus(TransactionStatus.SUBMITTING);
        return repository.save(transaction);
    }

    public Transaction markInMempool(Transaction transaction, String transactionHash,
            long submissionBlock, int submissionRetries) {
        transaction.setStatus(TransactionStatus.IN_MEMPOOL);
        transaction.setHash(transactionHash);
        transaction.setSubmissionBlock(submissionBlock);
        transaction.setSubmissionRetries(submissionRetries);
        transaction.setSubmittedAt(OffsetDateTime.now());
        return repository.save(transaction);
    }

    public Transaction markFailed(Transaction transaction, String errorMessage) {
        return markFailed(transaction, errorMessage, 0, null);
    }

    public Transaction markFailed(Transaction transaction, String errorMessage, int submissionRetries) {
        return markFailed(transaction, errorMessage, submissionRetries, null);
    }

    public Transaction markFailed(Transaction transaction, String errorMessage,
            int submissionRetries, OffsetDateTime firstFaultAt) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        fresh.setStatus(TransactionStatus.FAILED);
        fresh.setErrorMessage(errorMessage);
        fresh.setSubmissionRetries(submissionRetries);
        fresh.setFailedAt(OffsetDateTime.now());
        recordFirstFault(fresh, firstFaultAt);
        return repository.save(fresh);
    }

    public Transaction markAborted(Transaction transaction, String errorMessage, OffsetDateTime firstFaultAt) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        fresh.setStatus(TransactionStatus.CRYPTOGRAPHIC_ABORT);
        fresh.setErrorMessage(errorMessage);
        fresh.setSigningRetries(transaction.getSigningRetries());
        fresh.setFailedAt(OffsetDateTime.now());
        recordFirstFault(fresh, firstFaultAt);
        return repository.save(fresh);
    }

    public Transaction markVerificationAborted(Transaction transaction, String errorMessage,
            OffsetDateTime firstFaultAt) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        fresh.setStatus(TransactionStatus.VERIFICATION_ABORT);
        fresh.setErrorMessage(errorMessage);
        fresh.setSigningRetries(transaction.getSigningRetries());
        fresh.setFailedAt(OffsetDateTime.now());
        recordFirstFault(fresh, firstFaultAt);
        return repository.save(fresh);
    }

    private void recordFirstFault(Transaction transaction, OffsetDateTime firstFaultAt) {
        if (firstFaultAt != null && transaction.getFirstFaultAt() == null) {
            transaction.setFirstFaultAt(firstFaultAt);
        }
    }

    public int confirmTransactions(List<String> hashes, Long blockNumber) {
        return repository.confirmTransactions(hashes, blockNumber);
    }

    public Transaction incrementSweeperAttempts(Transaction transaction) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        int current = fresh.getSweeperAttempts() == null ? 0 : fresh.getSweeperAttempts();
        fresh.setSweeperAttempts(current + 1);
        fresh.setAmountEther(BigDecimal.ZERO);
        return repository.save(fresh);
    }

    public List<Transaction> findTransactionsByStatusOrderedByNonce(TransactionStatus status) {
        return repository.findByStatusOrderByNonceAsc(status);
    }

    public List<Transaction> findFailedTransactionsOrderedByNonce() {
        return repository.findByStatusInOrderByNonceAsc(List.of(
                TransactionStatus.CRYPTOGRAPHIC_ABORT,
                TransactionStatus.VERIFICATION_ABORT,
                TransactionStatus.FAILED));
    }

    public Optional<Transaction> findByFromAddressAndNonce(String fromAddress, Long nonce) {
        return repository.findByFromAddressAndNonce(fromAddress, nonce);
    }

    public List<Transaction> findLowestNonceInMempoolPerAddress() {
        return repository.findLowestNonceInMempoolPerAddress();
    }

    public List<Transaction> findAll() {
        return repository.findAll();
    }

}
