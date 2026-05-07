package uk.ac.herts.orchestrator.repository.dao;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import uk.ac.herts.orchestrator.repository.TransactionRepository;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.repository.model.TransactionStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class TransactionDaoTest {

    @Autowired
    private TransactionDao transactionDao;
    @Autowired
    private TransactionRepository transactionRepository;

    private static final String TO_ADDRESS = "0xabcdef";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(1.5);

    @Test
    void createTransaction_persistsWithCorrectFieldsAndStatusNew() {
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);

        assertThat(tx.getId()).isNotNull();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.NEW);
        assertThat(tx.getToAddress()).isEqualTo(TO_ADDRESS);
        assertThat(tx.getAmountEther()).isEqualByComparingTo(AMOUNT);
        assertThat(tx.getCreatedAt()).isNotNull();
        assertThat(tx.getUpdatedAt()).isNotNull();
        assertThat(tx.getVersion()).isNotNull();
        assertThat(tx.getErrorMessage()).isNull();
        assertThat(tx.getTransactionHash()).isNull();
    }

    @Test
    void createTransaction_capturesTraceIdFromMdc() {
        MDC.put("traceId", "test-trace-xyz");
        try {
            Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);
            assertThat(tx.getTraceId()).isEqualTo("test-trace-xyz");
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void createTransaction_withNoMdcTraceId_savesNullTraceId() {
        MDC.remove("traceId");
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);
        assertThat(tx.getTraceId()).isNull();
    }

    @Test
    void markSigning_changesStatusToSigningAndUpdatesTimestamp() {
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);

        Transaction result = transactionDao.markSigning(tx);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SIGNING);
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void markSigned_setsStatusSignedHexPayloadAndSignedAt() {
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);
        tx = transactionDao.markSigning(tx);

        Transaction result = transactionDao.markSigned(tx, "0xdeadbeef");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SIGNED);
        assertThat(result.getSignedHexPayload()).isEqualTo("0xdeadbeef");
        assertThat(result.getSignedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void markSubmitting_changesStatusToSubmitting() {
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);
        tx = transactionDao.markSigning(tx);
        tx = transactionDao.markSigned(tx, "0xpayload");

        Transaction result = transactionDao.markSubmitting(tx);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUBMITTING);
    }

    @Test
    void markSubmitted_setsStatusSubmittedTransactionHashAndSubmittedAt() {
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);
        tx = transactionDao.markSigning(tx);
        tx = transactionDao.markSigned(tx, "0xpayload");
        tx = transactionDao.markSubmitting(tx);

        EthSendTransaction ethSendTx = mock(EthSendTransaction.class);
        when(ethSendTx.getTransactionHash()).thenReturn("0xtxhash");

        Transaction result = transactionDao.markSubmitted(tx, ethSendTx);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUBMITTED);
        assertThat(result.getTransactionHash()).isEqualTo("0xtxhash");
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    void markConfirming_changesStatusToConfirming() {
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);
        tx = transactionDao.markSigning(tx);
        tx = transactionDao.markSigned(tx, "0xpayload");
        tx = transactionDao.markSubmitting(tx);
        EthSendTransaction ethSendTx = mock(EthSendTransaction.class);
        when(ethSendTx.getTransactionHash()).thenReturn("0xtxhash");
        tx = transactionDao.markSubmitted(tx, ethSendTx);

        Transaction result = transactionDao.markConfirming(tx);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.CONFIRMING);
    }

    @Test
    void markConfirmed_setsStatusConfirmedAndConfirmedAt() {
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);
        tx = transactionDao.markSigning(tx);
        tx = transactionDao.markSigned(tx, "0xpayload");
        tx = transactionDao.markSubmitting(tx);
        EthSendTransaction ethSendTx = mock(EthSendTransaction.class);
        when(ethSendTx.getTransactionHash()).thenReturn("0xtxhash");
        tx = transactionDao.markSubmitted(tx, ethSendTx);
        tx = transactionDao.markConfirming(tx);

        Transaction result = transactionDao.markConfirmed(tx, mock(TransactionReceipt.class));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void markFailed_setsStatusFailedWithErrorMessageAndFailedAt() {
        Transaction tx = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);

        Transaction result = transactionDao.markFailed(tx, "Something went wrong");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("Something went wrong");
        assertThat(result.getFailedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void markFailed_withStaleVersionEntity_succeedsWithoutOptimisticLockException() {
        // Reproduce the real-world scenario:
        // 1. createTransaction returns version=0 object
        // 2. markSigning runs in a NEW transaction, bumping the DB to version=1
        // but the original object reference is not updated (JPA merge returns
        // a new managed copy; the detached original stays at version=0)
        // 3. markFailed is called with the stale version=0 object that normally throws
        // ObjectOptimisticLockingFailureException
        Transaction staleRef = transactionDao.createTransaction(TO_ADDRESS, AMOUNT);

        // Advance DB version - ignore the returned (version=1) entity on purpose
        transactionDao.markSigning(staleRef);

        // staleRef.version == 0 still (merge returns a copy, original is unchanged)
        assertThatNoException()
                .isThrownBy(() -> transactionDao.markFailed(staleRef, "Signing failed"));

        Transaction saved = transactionRepository.findById(staleRef.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo("Signing failed");

        // Manual cleanup since this test is NOT_SUPPORTED (no rollback)
        transactionRepository.deleteById(staleRef.getId());
    }
}
