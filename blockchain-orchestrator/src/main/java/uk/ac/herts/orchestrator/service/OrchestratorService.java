package uk.ac.herts.orchestrator.service;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.api.filter.TraceIdFilter;
import uk.ac.herts.orchestrator.client.blockchain.BlockchainClient;
import uk.ac.herts.orchestrator.client.blockchain.NonceManager;
import uk.ac.herts.orchestrator.client.mpc.DsgCoordinator;
import uk.ac.herts.orchestrator.exception.SignatureGenerationException;
import uk.ac.herts.orchestrator.exception.BlockchainRpcException;
import uk.ac.herts.orchestrator.exception.TransactionSigningException;
import uk.ac.herts.orchestrator.exception.TransactionSubmissionException;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;
import uk.ac.herts.orchestrator.repository.entity.Transaction;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final BlockchainClient blockchainClient;
    private final TransactionDao transactionDao;
    private final MpcKeyRepository mpcKeyRepository;
    private final DsgCoordinator dsgCoordinator;
    private final NonceManager nonceManager;

    public SubmitTransactionResponse startTransaction(SubmitTransactionRequest request) {
        MpcKey mpcKey = mpcKeyRepository.findById(request.keyId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid keyId"));
        String fromAddress = mpcKey.getEthereumAddress();

        Transaction tx = transactionDao.createTransaction(fromAddress, request.toAddress(), request.amountEther());

        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        virtualThreadExecutor.execute(() -> {
            if (traceId != null) {
                MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, traceId);
            }
            try {
                Transaction signed = sign(tx, mpcKey);
                submitToMempool(signed, fromAddress);
            } catch (TransactionSigningException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SignatureGenerationException sge && sge.isVerificationFailure()) {
                    transactionDao.markVerificationAborted(tx, buildErrorMessage("Signing phase failed", e));
                } else {
                    transactionDao.markAborted(tx, buildErrorMessage("Signing phase failed", e));
                }
            } catch (TransactionSubmissionException e) {
                transactionDao.markFailed(tx, buildErrorMessage("Submission phase failed", e));
            } catch (Exception e) {
                transactionDao.markFailed(tx, buildErrorMessage("Unexpected error during transaction processing", e));
            } finally {
                MDC.clear();
            }
        });

        return SubmitTransactionResponse.builder()
                .transactionId(tx.getId())
                .toAddress(request.toAddress())
                .amountEther(request.amountEther())
                .status(tx.getStatus().toString())
                .build();
    }

    private Transaction sign(Transaction transaction, MpcKey mpcKey) {
        try {
            return dsgCoordinator.executeUnderConcurrencyLimit(() -> {
                long nonce = nonceManager.getAndIncrementNonce(mpcKey.getEthereumAddress());
                Transaction tx = transactionDao.markSigning(transaction, nonce);

                BigInteger gasPrice = blockchainClient.fetchGasPrice();
                BigInteger gasLimit = blockchainClient.getGasLimit();
                BigInteger valueWei = Convert.toWei(tx.getAmountEther(), Convert.Unit.ETHER)
                        .setScale(0, RoundingMode.HALF_UP)
                        .toBigInteger();

                RawTransaction rawTx = RawTransaction.createEtherTransaction(
                        BigInteger.valueOf(nonce),
                        gasPrice,
                        gasLimit,
                        tx.getToAddress(),
                        valueWei);

                byte[] encoded = TransactionEncoder.encode(rawTx);
                byte[] msgHash = org.web3j.crypto.Hash.sha3(encoded);

                transactionDao.markSigningStarted(tx);
                DsgCoordinator.DsgResult dsgResult = dsgCoordinator.executeDsg(mpcKey, msgHash);

                String hexPayload = Numeric.toHexString(TransactionEncoder.encode(rawTx, dsgResult.signature()));

                tx.setSigningRetries(dsgResult.retries());
                return transactionDao.markSigned(tx, hexPayload);
            });
        } catch (SignatureGenerationException e) {
            transaction.setSigningRetries(e.getRetries());
            throw new TransactionSigningException(
                    String.format("Signing failed for transaction_id=%s", transaction.getId()), e);
        } catch (Exception e) {
            throw new TransactionSigningException(
                    String.format("Signing failed for transaction_id=%s", transaction.getId()), e);
        }
    }

    private Transaction submitToMempool(Transaction transaction, String address) {
        transaction = transactionDao.markSubmitting(transaction);
        try {
            BlockchainClient.SubmissionResult submissionResult = blockchainClient
                    .submitRawTransaction(transaction.getSignedHexPayload(), address);
            transaction.setSubmissionRetries(submissionResult.retries());
            long submissionBlock = blockchainClient.fetchCurrentBlockNumber();
            return transactionDao.markInMempool(transaction, submissionResult.transactionHash(), submissionBlock);
        } catch (BlockchainRpcException e) {
            transaction.setSubmissionRetries(e.getRetries());
            throw new TransactionSubmissionException(
                    String.format("Submission failed for transaction_id=%s", transaction.getId()), e);
        } catch (Exception e) {
            throw new TransactionSubmissionException(
                    String.format("Submission failed for transaction_id=%s", transaction.getId()), e);
        }
    }

    private String buildErrorMessage(String phase, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(phase).append(".");

        String mainMsg = e.getMessage();
        if (mainMsg != null && !mainMsg.isEmpty()) {
            sb.append(" ").append(mainMsg);
        }

        Throwable cause = e.getCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && !causeMsg.isEmpty()) {
                sb.append(" (Root cause: ").append(cause.getClass().getSimpleName())
                        .append(" - ").append(causeMsg).append(")");
            }
        }

        return sb.toString();
    }

}
