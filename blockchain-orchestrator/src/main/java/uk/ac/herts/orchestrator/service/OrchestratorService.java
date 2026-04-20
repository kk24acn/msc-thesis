package uk.ac.herts.orchestrator.service;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import lombok.RequiredArgsConstructor;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.config.HardhatProperties;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.util.DsgCoordinator;
import uk.ac.herts.orchestrator.util.NonceManager;

@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final ConcurrentHashMap<String, Object> addressLocks = new ConcurrentHashMap<>();

    private final Web3j web3j;
    private final NonceManager nonceManager;
    private final HardhatProperties hardhatProperties;
    private final TransactionDao transactionDao;
    private final MpcKeyRepository mpcKeyRepository;
    private final DsgCoordinator dsgCoordinator;

    public SubmitTransactionResponse startTransaction(SubmitTransactionRequest request) {
        MpcKey mpcKey = mpcKeyRepository.findById(request.keyId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid keyId"));

        Transaction tx = transactionDao.createTransaction(request.toAddress(), request.amountEther());

        String address = mpcKey.getEthereumAddress();
        synchronized (addressLocks.computeIfAbsent(address, k -> new Object())) {
            try {
                sign(tx, mpcKey);
                submit(tx, address);
                confirm(tx);
            } catch (Exception e) {
                transactionDao.markFailed(tx, e.getMessage());
                throw e;
            }
        }

        return SubmitTransactionResponse.builder()
                .transactionId(tx.getId())
                .transactionHash(tx.getTransactionHash())
                .status(tx.getStatus().toString())
                .build();
    }

    private void sign(Transaction tx, MpcKey mpcKey) {
        transactionDao.markSigning(tx);

        BigInteger nonce = nonceManager.getCurrentNonce(mpcKey.getEthereumAddress());
        BigInteger gasPrice = fetchGasPrice();
        // BigInteger valueWei = Convert.toWei(tx.getAmountEther(),
        // Convert.Unit.ETHER).toBigIntegerExact();
        BigInteger valueWei = Convert.toWei(tx.getAmountEther(), Convert.Unit.ETHER)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toBigInteger();

        RawTransaction rawTx = RawTransaction.createEtherTransaction(
                nonce,
                gasPrice,
                BigInteger.valueOf(hardhatProperties.getGasLimit()),
                tx.getToAddress(),
                valueWei);

        byte[] encoded = TransactionEncoder.encode(rawTx);
        byte[] msgHash = org.web3j.crypto.Hash.sha3(encoded);
        byte[] sigBytes = dsgCoordinator.executeDsg(mpcKey, msgHash);

        Sign.SignatureData sigData = decodeSignature(sigBytes);
        String hexPayload = Numeric.toHexString(TransactionEncoder.encode(rawTx, sigData));

        transactionDao.markSigned(tx, hexPayload);
    }

    private void submit(Transaction transaction, String address) {
        transactionDao.markSubmitting(transaction);
        int attempt = 0;
        Duration delay = hardhatProperties.getRetryBackoff();

        while (attempt <= hardhatProperties.getMaxRetries()) {
            try {
                EthSendTransaction result = web3j.ethSendRawTransaction(transaction.getSignedHexPayload()).send();
                if (result.hasError()) {
                    transactionDao.markFailed(transaction, result.getError().getMessage());
                    throw new IllegalStateException(result.getError().getMessage());
                }
                transactionDao.markSubmitted(transaction, result);
                nonceManager.incrementNonce(address);
                return;
            } catch (Exception e) {
                if (attempt == hardhatProperties.getMaxRetries()) {
                    throw new RuntimeException("Submission failed", e);
                }
                sleep(delay);
                delay = delay.multipliedBy(2);
                attempt++;
            }
        }
        throw new IllegalStateException("Max retries exceeded");
    }

    private void confirm(Transaction transaction) {
        transactionDao.markConfirming(transaction);
        long deadline = System.nanoTime() + hardhatProperties.getTransactionTimeout().toNanos();

        while (System.nanoTime() < deadline) {
            try {
                Optional<TransactionReceipt> receipt = web3j.ethGetTransactionReceipt(transaction.getTransactionHash())
                        .send().getTransactionReceipt();

                if (receipt.isPresent()) {
                    transactionDao.markConfirmed(transaction, receipt.get());
                    return;
                }
                sleep(hardhatProperties.getReceiptPollInterval());
            } catch (Exception e) {
                throw new RuntimeException("Confirmation failed", e);
            }
        }
        throw new RuntimeException("Confirmation timeout");
    }

    private BigInteger fetchGasPrice() {
        try {
            return web3j.ethGasPrice().send().getGasPrice();
        } catch (IOException e) {
            throw new RuntimeException("Gas price fetch failed", e);
        }
    }

    private Sign.SignatureData decodeSignature(byte[] signatureBytes) {
        if (signatureBytes.length != 65) {
            throw new IllegalStateException("Invalid signature length");
        }
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(signatureBytes, 0, r, 0, 32);
        System.arraycopy(signatureBytes, 32, s, 0, 32);
        byte v = signatureBytes[64];
        return new Sign.SignatureData(v, r, s);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}