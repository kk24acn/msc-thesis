package uk.ac.herts.orchestrator.service;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
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
            } catch (Exception e) {
                transactionDao.markFailed(tx, e.getMessage());
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
            byte[] sigBytes = dsgCoordinator.executeDsg(mpcKey, msgHash);

            Sign.SignatureData sigData = decodeSignature(sigBytes);
            String hexPayload = Numeric.toHexString(TransactionEncoder.encode(rawTx, sigData));

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
            EthSendTransaction result = hardhatConnector.submitRawTransaction(transaction.getSignedHexPayload(),
                    address);
            return transactionDao.markSubmitted(transaction, result);
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
        } catch (TransactionSubmissionException e) {
            throw e;
        } catch (Exception e) {
            throw new TransactionSubmissionException("Confirmation failed for transaction " + transaction.getId(), e);
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

}
