package uk.ac.herts.orchestrator.repository.dao;

import org.springframework.stereotype.Component;

import uk.ac.herts.orchestrator.api.filter.TraceIdFilter;
import uk.ac.herts.orchestrator.repository.TransactionRepository;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.repository.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;

@Component
public class TransactionDao {

    private final TransactionRepository repository;

    public TransactionDao(TransactionRepository repository) {
        this.repository = repository;
    }

    public Transaction createTransaction(String fromAddress, String toAddress, BigDecimal amountEther) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Transaction newTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .amountEther(amountEther)
                .status(TransactionStatus.NEW)
                .createdAt(now)
                .updatedAt(now)
                .traceId(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY))
                .build();
        return repository.save(newTransaction);
    }

    public Transaction markSigning(Transaction transaction, Long nonce) {
        transaction.setStatus(TransactionStatus.SIGNING);
        transaction.setNonce(nonce);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markSigningStarted(Transaction transaction) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        fresh.setSigningStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        fresh.touchUpdatedAt();
        return repository.save(fresh);
    }

    public Transaction markSigned(Transaction transaction, String hexPayload) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        fresh.setStatus(TransactionStatus.SIGNED);
        fresh.setSignedHexPayload(hexPayload);
        fresh.setSigningRetries(transaction.getSigningRetries());
        fresh.setSignedAt(now);
        fresh.setUpdatedAt(now);
        return repository.save(fresh);
    }

    public Transaction markSubmitting(Transaction transaction) {
        transaction.setStatus(TransactionStatus.SUBMITTING);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markInMempool(Transaction transaction, String transactionHash,
            long submissionBlock) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        transaction.setStatus(TransactionStatus.IN_MEMPOOL);
        transaction.setHash(transactionHash);
        transaction.setSubmissionBlock(submissionBlock);
        transaction.setSubmittedAt(now);
        transaction.setUpdatedAt(now);
        return repository.save(transaction);
    }

    public Transaction markStalled(Transaction transaction) {
        transaction.setStatus(TransactionStatus.STALLED);
        transaction.touchUpdatedAt();
        return repository.save(transaction);
    }

    public Transaction markFailed(Transaction transaction, String errorMessage) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        fresh.setStatus(TransactionStatus.FAILED);
        fresh.setErrorMessage(errorMessage);
        fresh.setSubmissionRetries(transaction.getSubmissionRetries());
        fresh.setFailedAt(now);
        fresh.setUpdatedAt(now);
        return repository.save(fresh);
    }

    public Transaction markAborted(Transaction transaction, String errorMessage) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        fresh.setStatus(TransactionStatus.CRYPTOGRAPHIC_ABORT);
        fresh.setErrorMessage(errorMessage);
        fresh.setSigningRetries(transaction.getSigningRetries());
        fresh.setUpdatedAt(now);
        return repository.save(fresh);
    }

    public Transaction markVerificationAborted(Transaction transaction, String errorMessage) {
        Transaction fresh = repository.findById(transaction.getId()).orElse(transaction);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        fresh.setStatus(TransactionStatus.VERIFICATION_ABORT);
        fresh.setErrorMessage(errorMessage);
        fresh.setSigningRetries(transaction.getSigningRetries());
        fresh.setUpdatedAt(now);
        return repository.save(fresh);
    }

    public int confirmTransactions(List<String> hashes, Long blockNumber) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repository.confirmTransactions(hashes, blockNumber, now);
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
