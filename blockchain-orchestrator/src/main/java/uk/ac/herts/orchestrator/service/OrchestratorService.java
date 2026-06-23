package uk.ac.herts.orchestrator.service;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.web3j.crypto.RawTransaction;
import org.web3j.utils.Convert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.client.blockchain.BlockchainClient;
import uk.ac.herts.orchestrator.client.blockchain.NonceManager;
import uk.ac.herts.orchestrator.client.mpc.TransactionSigner;
import uk.ac.herts.orchestrator.exception.mpc.SignatureGenerationException;
import uk.ac.herts.orchestrator.exception.mpc.SignatureVerificationException;
import uk.ac.herts.orchestrator.exception.transaction.TransactionSigningException;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.util.ErrorUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final BlockchainClient blockchainClient;
    private final TransactionDao transactionDao;
    private final MpcKeyRepository mpcKeyRepository;
    private final NonceManager nonceManager;
    private final TransactionSigner transactionSigner;

    public SubmitTransactionResponse startTransaction(SubmitTransactionRequest request) {
        MpcKey mpcKey = mpcKeyRepository.findById(request.keyId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid keyId"));
        String fromAddress = mpcKey.getEthereumAddress();

        Transaction tx = transactionDao.createTransaction(fromAddress, request.toAddress(), request.amountEther());

        virtualThreadExecutor.execute(() -> {
            if (tx.getTraceId() != null) {
                MDC.put("traceId", tx.getTraceId().toString());
            }
            try {
                sign(tx, mpcKey);
            } catch (TransactionSigningException e) {
                Throwable cause = e.getCause();
                OffsetDateTime firstFaultAt = cause instanceof SignatureGenerationException sigEx
                        ? sigEx.getFirstFaultAt()
                        : null;
                if (cause instanceof SignatureVerificationException) {
                    transactionDao.markVerificationAborted(tx, ErrorUtils.buildErrorMessage("Signing phase failed", e),
                            firstFaultAt);
                } else {
                    transactionDao.markAborted(tx, ErrorUtils.buildErrorMessage("Signing phase failed", e),
                            firstFaultAt);
                }
            } catch (Exception e) {
                transactionDao.markFailed(tx,
                        ErrorUtils.buildErrorMessage("Unexpected error during transaction processing", e));
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

            TransactionSigner.SignResult signResult = transactionSigner.sign(rawTx, tx, mpcKey);
            return transactionDao.markSigned(tx, signResult.hexPayload(), signResult.retries(),
                    signResult.firstFaultAt());
        } catch (SignatureGenerationException e) {
            transaction.setSigningRetries(e.getRetries());
            throw new TransactionSigningException(
                    String.format("Signing failed for transaction_id=%s", transaction.getId()), e);
        } catch (Exception e) {
            throw new TransactionSigningException(
                    String.format("Signing failed for transaction_id=%s", transaction.getId()), e);
        }
    }

}
