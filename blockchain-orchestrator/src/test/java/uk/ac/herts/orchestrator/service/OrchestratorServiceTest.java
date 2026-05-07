package uk.ac.herts.orchestrator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.exception.TransactionConfirmationException;
import uk.ac.herts.orchestrator.exception.TransactionSigningException;
import uk.ac.herts.orchestrator.exception.TransactionSubmissionException;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.repository.model.TransactionStatus;
import uk.ac.herts.orchestrator.util.DsgCoordinator;
import uk.ac.herts.orchestrator.util.HardhatConnector;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock
    private HardhatConnector hardhatConnector;
    @Mock
    private TransactionDao transactionDao;
    @Mock
    private MpcKeyRepository mpcKeyRepository;
    @Mock
    private DsgCoordinator dsgCoordinator;

    @InjectMocks
    private OrchestratorService service;

    private static final String KEY_ID = "test-key-1";
    private static final String ETH_ADDRESS = "0x1234567890123456789012345678901234567890";
    private static final String TO_ADDRESS = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
    private static final String TX_HASH = "0xdeadbeef";
    private static final byte[] VALID_SIG_65 = new byte[65];

    private MpcKey mpcKey;
    private SubmitTransactionRequest request;

    @BeforeEach
    void setUp() {
        mpcKey = new MpcKey();
        mpcKey.setKeyId(KEY_ID);
        mpcKey.setEthereumAddress(ETH_ADDRESS);
        mpcKey.setThreshold(2);
        mpcKey.setTotalParties(3);
        mpcKey.setDerivationPath("m/0");

        request = new SubmitTransactionRequest(KEY_ID, TO_ADDRESS, BigDecimal.valueOf(0.1));
    }

    @Test
    void startTransaction_happyPath_returnsConfirmedResponse() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTx(txId, TransactionStatus.NEW, 0L);
        Transaction txSigning = buildTx(txId, TransactionStatus.SIGNING, 1L);
        Transaction txSigned = buildTx(txId, TransactionStatus.SIGNED, 2L);
        Transaction txSubmitting = buildTx(txId, TransactionStatus.SUBMITTING, 3L);
        Transaction txSubmitted = buildTx(txId, TransactionStatus.SUBMITTED, 4L);
        txSubmitted.setTransactionHash(TX_HASH);
        Transaction txConfirming = buildTx(txId, TransactionStatus.CONFIRMING, 5L);
        txConfirming.setTransactionHash(TX_HASH);
        Transaction txConfirmed = buildTx(txId, TransactionStatus.CONFIRMED, 6L);
        txConfirmed.setTransactionHash(TX_HASH);

        EthSendTransaction ethSendTx = mock(EthSendTransaction.class);

        when(mpcKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(mpcKey));
        when(transactionDao.createTransaction(TO_ADDRESS, BigDecimal.valueOf(0.1))).thenReturn(tx);
        when(transactionDao.markSigning(tx)).thenReturn(txSigning);
        when(hardhatConnector.getCurrentNonce(ETH_ADDRESS)).thenReturn(BigInteger.ZERO);
        when(hardhatConnector.fetchGasPrice()).thenReturn(BigInteger.TEN);
        when(hardhatConnector.getGasLimit()).thenReturn(BigInteger.valueOf(21000));
        when(dsgCoordinator.executeDsg(eq(mpcKey), any())).thenReturn(new DsgCoordinator.DsgResult(VALID_SIG_65, 1));
        when(transactionDao.markSigned(eq(txSigning), any())).thenReturn(txSigned);
        when(transactionDao.markSubmitting(txSigned)).thenReturn(txSubmitting);
        when(hardhatConnector.submitRawTransaction(any(), eq(ETH_ADDRESS)))
                .thenReturn(new HardhatConnector.SubmissionResult(ethSendTx, 1));
        when(transactionDao.markSubmitted(eq(txSubmitting), eq(ethSendTx))).thenReturn(txSubmitted);
        when(transactionDao.markConfirming(txSubmitted)).thenReturn(txConfirming);
        when(hardhatConnector.waitForConfirmation(TX_HASH)).thenReturn(mock(TransactionReceipt.class));
        when(transactionDao.markConfirmed(eq(txConfirming), any())).thenReturn(txConfirmed);

        SubmitTransactionResponse response = service.startTransaction(request);

        assertThat(response.transactionId()).isEqualTo(txId);
        assertThat(response.transactionHash()).isEqualTo(TX_HASH);
        assertThat(response.toAddress()).isEqualTo(TO_ADDRESS);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        verify(transactionDao, never()).markFailed(any(), any());
    }

    @Test
    void startTransaction_unknownKeyId_throwsIllegalArgumentException() {
        when(mpcKeyRepository.findById(KEY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid keyId");

        verifyNoInteractions(transactionDao, hardhatConnector, dsgCoordinator);
    }

    @Test
    void startTransaction_signingFails_throwsTransactionSigningException() {
        Transaction tx = buildTx(UUID.randomUUID(), TransactionStatus.NEW, 0L);
        Transaction txSigning = buildTx(tx.getId(), TransactionStatus.SIGNING, 1L);

        when(mpcKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(mpcKey));
        when(transactionDao.createTransaction(any(), any())).thenReturn(tx);
        when(transactionDao.markSigning(tx)).thenReturn(txSigning);
        when(hardhatConnector.getCurrentNonce(any())).thenThrow(new RuntimeException("RPC unavailable"));
        when(transactionDao.markFailed(any(), any())).thenReturn(buildTx(tx.getId(), TransactionStatus.FAILED, 2L));

        assertThatThrownBy(() -> service.startTransaction(request))
                .isInstanceOf(TransactionSigningException.class);
    }

    @Test
    void startTransaction_signingFails_callsMarkFailedOnOriginalTxWithDescriptiveMessage() {
        Transaction tx = buildTx(UUID.randomUUID(), TransactionStatus.NEW, 0L);
        Transaction txSigning = buildTx(tx.getId(), TransactionStatus.SIGNING, 1L);

        when(mpcKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(mpcKey));
        when(transactionDao.createTransaction(any(), any())).thenReturn(tx);
        when(transactionDao.markSigning(tx)).thenReturn(txSigning);
        when(hardhatConnector.getCurrentNonce(any())).thenThrow(new RuntimeException("RPC unavailable"));
        when(transactionDao.markFailed(any(), any())).thenReturn(buildTx(tx.getId(), TransactionStatus.FAILED, 2L));

        assertThatThrownBy(() -> service.startTransaction(request)).isInstanceOf(TransactionSigningException.class);

        verify(transactionDao).markFailed(
                eq(tx),
                argThat(msg -> msg.contains("Signing phase failed") && msg.contains("RPC unavailable")));
    }

    @Test
    void startTransaction_signingFails_withInvalidSignatureLength_throwsTransactionSigningException() {
        byte[] shortSig = new byte[32]; // not 65 bytes
        Transaction tx = buildTx(UUID.randomUUID(), TransactionStatus.NEW, 0L);
        Transaction txSigning = buildTx(tx.getId(), TransactionStatus.SIGNING, 1L);

        when(mpcKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(mpcKey));
        when(transactionDao.createTransaction(any(), any())).thenReturn(tx);
        when(transactionDao.markSigning(tx)).thenReturn(txSigning);
        when(hardhatConnector.getCurrentNonce(ETH_ADDRESS)).thenReturn(BigInteger.ZERO);
        when(hardhatConnector.fetchGasPrice()).thenReturn(BigInteger.TEN);
        when(hardhatConnector.getGasLimit()).thenReturn(BigInteger.valueOf(21000));
        when(dsgCoordinator.executeDsg(eq(mpcKey), any())).thenReturn(new DsgCoordinator.DsgResult(shortSig, 1));
        when(transactionDao.markFailed(any(), any())).thenReturn(buildTx(tx.getId(), TransactionStatus.FAILED, 2L));

        assertThatThrownBy(() -> service.startTransaction(request))
                .isInstanceOf(TransactionSigningException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void startTransaction_submissionFails_throwsTransactionSubmissionException() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTx(txId, TransactionStatus.NEW, 0L);
        Transaction txSigning = buildTx(txId, TransactionStatus.SIGNING, 1L);
        Transaction txSigned = buildTx(txId, TransactionStatus.SIGNED, 2L);
        Transaction txSubmitting = buildTx(txId, TransactionStatus.SUBMITTING, 3L);

        when(mpcKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(mpcKey));
        when(transactionDao.createTransaction(any(), any())).thenReturn(tx);
        when(transactionDao.markSigning(tx)).thenReturn(txSigning);
        when(hardhatConnector.getCurrentNonce(ETH_ADDRESS)).thenReturn(BigInteger.ZERO);
        when(hardhatConnector.fetchGasPrice()).thenReturn(BigInteger.TEN);
        when(hardhatConnector.getGasLimit()).thenReturn(BigInteger.valueOf(21000));
        when(dsgCoordinator.executeDsg(eq(mpcKey), any())).thenReturn(new DsgCoordinator.DsgResult(VALID_SIG_65, 1));
        when(transactionDao.markSigned(eq(txSigning), any())).thenReturn(txSigned);
        when(transactionDao.markSubmitting(txSigned)).thenReturn(txSubmitting);
        when(hardhatConnector.submitRawTransaction(any(), any())).thenThrow(new RuntimeException("Node unreachable"));
        when(transactionDao.markFailed(any(), any())).thenReturn(buildTx(txId, TransactionStatus.FAILED, 4L));

        assertThatThrownBy(() -> service.startTransaction(request))
                .isInstanceOf(TransactionSubmissionException.class);

        verify(transactionDao).markFailed(
                eq(txSigned),
                argThat(msg -> msg.contains("Submission phase failed") && msg.contains("Node unreachable")));
    }

    @Test
    void startTransaction_confirmationFails_throwsTransactionConfirmationException() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTx(txId, TransactionStatus.NEW, 0L);
        Transaction txSigning = buildTx(txId, TransactionStatus.SIGNING, 1L);
        Transaction txSigned = buildTx(txId, TransactionStatus.SIGNED, 2L);
        Transaction txSubmitting = buildTx(txId, TransactionStatus.SUBMITTING, 3L);
        Transaction txSubmitted = buildTx(txId, TransactionStatus.SUBMITTED, 4L);
        txSubmitted.setTransactionHash(TX_HASH);
        Transaction txConfirming = buildTx(txId, TransactionStatus.CONFIRMING, 5L);
        txConfirming.setTransactionHash(TX_HASH);

        EthSendTransaction ethSendTx = mock(EthSendTransaction.class);

        when(mpcKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(mpcKey));
        when(transactionDao.createTransaction(any(), any())).thenReturn(tx);
        when(transactionDao.markSigning(tx)).thenReturn(txSigning);
        when(hardhatConnector.getCurrentNonce(ETH_ADDRESS)).thenReturn(BigInteger.ZERO);
        when(hardhatConnector.fetchGasPrice()).thenReturn(BigInteger.TEN);
        when(hardhatConnector.getGasLimit()).thenReturn(BigInteger.valueOf(21000));
        when(dsgCoordinator.executeDsg(eq(mpcKey), any())).thenReturn(new DsgCoordinator.DsgResult(VALID_SIG_65, 1));
        when(transactionDao.markSigned(eq(txSigning), any())).thenReturn(txSigned);
        when(transactionDao.markSubmitting(txSigned)).thenReturn(txSubmitting);
        when(hardhatConnector.submitRawTransaction(any(), eq(ETH_ADDRESS)))
                .thenReturn(new HardhatConnector.SubmissionResult(ethSendTx, 1));
        when(transactionDao.markSubmitted(eq(txSubmitting), eq(ethSendTx))).thenReturn(txSubmitted);
        when(transactionDao.markConfirming(txSubmitted)).thenReturn(txConfirming);
        when(hardhatConnector.waitForConfirmation(TX_HASH))
                .thenThrow(new RuntimeException("Timeout waiting for receipt"));
        when(transactionDao.markFailed(any(), any())).thenReturn(buildTx(txId, TransactionStatus.FAILED, 6L));

        assertThatThrownBy(() -> service.startTransaction(request))
                .isInstanceOf(TransactionConfirmationException.class);

        verify(transactionDao).markFailed(
                eq(txSubmitted),
                argThat(msg -> msg.contains("Confirmation phase failed")
                        && msg.contains("Timeout waiting for receipt")));
    }

    @Test
    void startTransaction_recordsSigningAndSubmissionAttempts() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTx(txId, TransactionStatus.NEW, 0L);
        Transaction txSigning = buildTx(txId, TransactionStatus.SIGNING, 1L);
        Transaction txSigned = buildTx(txId, TransactionStatus.SIGNED, 2L);
        Transaction txSubmitting = buildTx(txId, TransactionStatus.SUBMITTING, 3L);
        Transaction txSubmitted = buildTx(txId, TransactionStatus.SUBMITTED, 4L);
        txSubmitted.setTransactionHash(TX_HASH);
        Transaction txConfirming = buildTx(txId, TransactionStatus.CONFIRMING, 5L);
        txConfirming.setTransactionHash(TX_HASH);
        Transaction txConfirmed = buildTx(txId, TransactionStatus.CONFIRMED, 6L);
        txConfirmed.setTransactionHash(TX_HASH);

        EthSendTransaction ethSendTx = mock(EthSendTransaction.class);

        when(mpcKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(mpcKey));
        when(transactionDao.createTransaction(any(), any())).thenReturn(tx);
        when(transactionDao.markSigning(tx)).thenReturn(txSigning);
        when(hardhatConnector.getCurrentNonce(ETH_ADDRESS)).thenReturn(BigInteger.ZERO);
        when(hardhatConnector.fetchGasPrice()).thenReturn(BigInteger.TEN);
        when(hardhatConnector.getGasLimit()).thenReturn(BigInteger.valueOf(21000));
        // DSG needed 3 attempts
        when(dsgCoordinator.executeDsg(eq(mpcKey), any())).thenReturn(new DsgCoordinator.DsgResult(VALID_SIG_65, 3));
        when(transactionDao.markSigned(eq(txSigning), any())).thenReturn(txSigned);
        when(transactionDao.markSubmitting(txSigned)).thenReturn(txSubmitting);
        // Submission needed 2 attempts
        when(hardhatConnector.submitRawTransaction(any(), eq(ETH_ADDRESS)))
                .thenReturn(new HardhatConnector.SubmissionResult(ethSendTx, 2));
        when(transactionDao.markSubmitted(eq(txSubmitting), eq(ethSendTx))).thenReturn(txSubmitted);
        when(transactionDao.markConfirming(txSubmitted)).thenReturn(txConfirming);
        when(hardhatConnector.waitForConfirmation(TX_HASH)).thenReturn(mock(TransactionReceipt.class));
        when(transactionDao.markConfirmed(eq(txConfirming), any())).thenReturn(txConfirmed);

        service.startTransaction(request);

        verify(transactionDao).markSigned(argThat(t -> t.getSigningAttempts() == 3), any());
        verify(transactionDao).markSubmitted(argThat(t -> t.getSubmissionAttempts() == 2), any());
    }

    private Transaction buildTx(UUID id, TransactionStatus status, long version) {
        return Transaction.builder()
                .id(id)
                .toAddress(TO_ADDRESS)
                .amountEther(BigDecimal.valueOf(0.1))
                .status(status)
                .version(version)
                .build();
    }
}
