package uk.ac.herts.orchestrator.util;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.config.HardhatProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class HardhatConnector {

    private final Web3j web3j;
    private final HardhatProperties hardhatProperties;

    public record SubmissionResult(EthSendTransaction transaction, int attempts) {
    }

    public BigInteger fetchGasPrice() {
        try {
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            log.debug("Gas price fetched: {} wei", gasPrice);
            return gasPrice;
        } catch (IOException e) {
            throw new RuntimeException("Gas price fetch failed", e);
        }
    }

    public long fetchCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber().longValue();
        } catch (IOException e) {
            throw new RuntimeException("Block number fetch failed", e);
        }
    }

    public BigInteger getGasLimit() {
        return BigInteger.valueOf(hardhatProperties.getGasLimit());
    }

    public SubmissionResult submitRawTransaction(String signedHexPayload, String fromAddress) {
        log.info("Starting transaction submission from address: {}", fromAddress);

        int attempt = 0;
        int maxRetries = hardhatProperties.getMaxRetries();
        Duration delay = hardhatProperties.getRetryBackoff();
        Exception lastException = null;

        while (attempt++ < maxRetries) {
            try {
                log.debug("Transaction submission attempt {}/{}", attempt, maxRetries);
                EthSendTransaction result = web3j.ethSendRawTransaction(signedHexPayload).send();
                if (result.hasError()) {
                    String errorMsg = result.getError().getMessage();
                    throw new IllegalStateException(
                            String.format("Transaction submission error on attempt %d: %s", attempt, errorMsg));
                }
                log.info("Transaction submitted successfully on attempt {}. Hash: {}",
                        attempt, result.getTransactionHash());
                return new SubmissionResult(result, attempt);
            } catch (Exception e) {
                lastException = e;
                if (attempt == maxRetries) {
                    throw new RuntimeException("Submission failed after " + maxRetries + " attempts", e);
                }

                log.debug("Submission attempt {} failed, retrying in {} ms", attempt, delay.toMillis(), e);
                sleep(delay);
                delay = delay.multipliedBy(2);
            }
        }

        throw new RuntimeException(String.format("Transaction submission failed after %d attempts",
                attempt - 1), lastException);
    }

    public TransactionReceipt waitForConfirmation(String txHash) {
        log.info("Waiting for transaction confirmation. Hash: {}", txHash);

        int pollCount = 0;
        long timeoutMs = System.currentTimeMillis() + hardhatProperties.getRequestTimeout().toMillis();

        while (System.currentTimeMillis() < timeoutMs) {
            try {
                Optional<TransactionReceipt> receipt = web3j
                        .ethGetTransactionReceipt(txHash)
                        .send()
                        .getTransactionReceipt();
                pollCount++;

                if (receipt.isPresent()) {
                    log.info("Transaction confirmed after {} polls. Block: {}, Status: {}",
                            pollCount, receipt.get().getBlockNumber(),
                            receipt.get().isStatusOK() ? "success" : "failed");
                    return receipt.get();
                }
                sleep(hardhatProperties.getReceiptPollInterval());
            } catch (Exception e) {
                throw new RuntimeException("Confirmation failed", e);
            }
        }
        throw new RuntimeException(String.format("Transaction confirmation timeout after %d ms for hash %s",
                hardhatProperties.getRequestTimeout().toMillis(), txHash));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
