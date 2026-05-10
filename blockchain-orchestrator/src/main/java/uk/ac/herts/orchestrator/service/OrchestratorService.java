package uk.ac.herts.orchestrator.service;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.exception.TransactionSigningException;
import uk.ac.herts.orchestrator.exception.TransactionSubmissionException;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.util.DsgCoordinator;
import uk.ac.herts.orchestrator.util.HardhatConnector;
import uk.ac.herts.orchestrator.util.NonceManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final HardhatConnector hardhatConnector;
    private final TransactionDao transactionDao;
    private final MpcKeyRepository mpcKeyRepository;
    private final DsgCoordinator dsgCoordinator;
    private final NonceManager nonceManager;

    public SubmitTransactionResponse startTransaction(SubmitTransactionRequest request) {
        MpcKey mpcKey = mpcKeyRepository.findById(request.keyId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid keyId"));
        String fromAddress = mpcKey.getEthereumAddress();

        Transaction tx = transactionDao.createTransaction(fromAddress, request.toAddress(), request.amountEther());

        String traceId = MDC.get("traceId");
        virtualThreadExecutor.execute(() -> {
            if (traceId != null) {
                MDC.put("traceId", traceId);
            }
            try {
                Transaction signed = sign(tx, mpcKey);
                submitToMempool(signed, fromAddress);
            } catch (TransactionSigningException e) {
                transactionDao.markAborted(tx, buildErrorMessage("Signing phase failed", e));
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

    private Transaction sign(Transaction tx, MpcKey mpcKey) {
        long nonce = nonceManager.getAndIncrementNonce(mpcKey.getEthereumAddress());
        tx = transactionDao.markSigning(tx, nonce);

        try {
            BigInteger gasPrice = hardhatConnector.fetchGasPrice();
            BigInteger gasLimit = hardhatConnector.getGasLimit();
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

            Transaction signingTx = tx;
            DsgCoordinator.DsgResult dsgResult = dsgCoordinator.executeDsg(mpcKey, msgHash,
                    () -> transactionDao.markSigningStarted(signingTx));

            Sign.SignatureData sigData = decodeSignature(dsgResult.signature());
            String hexPayload = Numeric.toHexString(TransactionEncoder.encode(rawTx, sigData));

            tx.setSigningAttempts(dsgResult.attempts());
            return transactionDao.markSigned(tx, hexPayload);
        } catch (TransactionSigningException e) {
            throw e;
        } catch (Exception e) {
            throw new TransactionSigningException("Signing failed for transaction " + tx.getId(), e);
        }
    }

    private Transaction submitToMempool(Transaction transaction, String address) {
        transaction = transactionDao.markSubmitting(transaction);
        try {
            HardhatConnector.SubmissionResult submissionResult = hardhatConnector.submitRawTransaction(
                    transaction.getSignedHexPayload(), address);
            transaction.setSubmissionAttempts(submissionResult.attempts());
            long submissionBlock = hardhatConnector.fetchCurrentBlockNumber();
            return transactionDao.markInMempool(transaction, submissionResult.transaction(), submissionBlock);
        } catch (TransactionSubmissionException e) {
            throw e;
        } catch (Exception e) {
            throw new TransactionSubmissionException("Submission failed for transaction " + transaction.getId(), e);
        }
    }

    private Sign.SignatureData decodeSignature(byte[] signatureBytes) {
        if (signatureBytes.length != 65) {
            throw new TransactionSigningException(
                    String.format("Signature length of %d bytes is not equal to required 65 bytes",
                            signatureBytes.length),
                    new IllegalArgumentException());
        }
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(signatureBytes, 0, r, 0, 32);
        System.arraycopy(signatureBytes, 32, s, 0, 32);
        byte v = signatureBytes[64];
        return new Sign.SignatureData(v, r, s);
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
