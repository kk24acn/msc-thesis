package uk.ac.herts.orchestrator.service;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.exception.TransactionConfirmationException;
import uk.ac.herts.orchestrator.exception.TransactionSigningException;
import uk.ac.herts.orchestrator.exception.TransactionSubmissionException;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.util.DsgCoordinator;
import uk.ac.herts.orchestrator.util.HardhatConnector;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final ConcurrentHashMap<String, Object> addressLocks = new ConcurrentHashMap<>();

    private final HardhatConnector hardhatConnector;
    private final TransactionDao transactionDao;
    private final MpcKeyRepository mpcKeyRepository;
    private final DsgCoordinator dsgCoordinator;

    public SubmitTransactionResponse startTransaction(SubmitTransactionRequest request) {
        MpcKey mpcKey = mpcKeyRepository.findById(request.keyId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid keyId"));
        String fromAddress = mpcKey.getEthereumAddress();

        Transaction tx = transactionDao.createTransaction(request.toAddress(), request.amountEther());
        synchronized (addressLocks.computeIfAbsent(fromAddress, k -> new Object())) {
            try {
                tx = sign(tx, mpcKey);
                tx = submit(tx, fromAddress);
                tx = confirm(tx);
            } catch (TransactionSigningException e) {
                String errorMsg = buildErrorMessage("Signing phase failed", e);
                transactionDao.markFailed(tx, errorMsg);
                throw e;
            } catch (TransactionSubmissionException e) {
                String errorMsg = buildErrorMessage("Submission phase failed", e);
                transactionDao.markFailed(tx, errorMsg);
                throw e;
            } catch (TransactionConfirmationException e) {
                String errorMsg = buildErrorMessage("Confirmation phase failed", e);
                transactionDao.markFailed(tx, errorMsg);
                throw e;
            } catch (Exception e) {
                String errorMsg = buildErrorMessage("Unexpected error during transaction processing", e);
                transactionDao.markFailed(tx, errorMsg);
                throw e;
            }
        }

        return SubmitTransactionResponse.builder()
                .transactionId(tx.getId())
                .transactionHash(tx.getTransactionHash())
                .toAddress(request.toAddress())
                .amountEther(request.amountEther())
                .status(tx.getStatus().toString())
                .build();
    }

    private Transaction sign(Transaction tx, MpcKey mpcKey) {
        tx = transactionDao.markSigning(tx);
        try {
            BigInteger nonce = hardhatConnector.getCurrentNonce(mpcKey.getEthereumAddress());
            BigInteger gasPrice = hardhatConnector.fetchGasPrice();
            BigInteger gasLimit = hardhatConnector.getGasLimit();
            BigInteger valueWei = Convert.toWei(tx.getAmountEther(), Convert.Unit.ETHER)
                    .setScale(0, RoundingMode.HALF_UP)
                    .toBigInteger();

            RawTransaction rawTx = RawTransaction.createEtherTransaction(
                    nonce,
                    gasPrice,
                    gasLimit,
                    tx.getToAddress(),
                    valueWei);

            byte[] encoded = TransactionEncoder.encode(rawTx);
            byte[] msgHash = org.web3j.crypto.Hash.sha3(encoded);
            DsgCoordinator.DsgResult dsgResult = dsgCoordinator.executeDsg(mpcKey, msgHash);

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

    private Transaction submit(Transaction transaction, String address) {
        transaction = transactionDao.markSubmitting(transaction);
        try {
            HardhatConnector.SubmissionResult submissionResult = hardhatConnector.submitRawTransaction(
                    transaction.getSignedHexPayload(), address);
            transaction.setSubmissionAttempts(submissionResult.attempts());
            return transactionDao.markSubmitted(transaction, submissionResult.transaction());
        } catch (TransactionSubmissionException e) {
            throw e;
        } catch (Exception e) {
            throw new TransactionSubmissionException("Submission failed for transaction " + transaction.getId(), e);
        }
    }

    private Transaction confirm(Transaction transaction) {
        transaction = transactionDao.markConfirming(transaction);
        try {
            TransactionReceipt receipt = hardhatConnector.waitForConfirmation(transaction.getTransactionHash());
            return transactionDao.markConfirmed(transaction, receipt);
        } catch (TransactionConfirmationException e) {
            throw e;
        } catch (Exception e) {
            throw new TransactionConfirmationException("Confirmation failed for transaction " + transaction.getId(), e);
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
