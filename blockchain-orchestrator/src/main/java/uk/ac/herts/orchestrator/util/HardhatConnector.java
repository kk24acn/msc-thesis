package uk.ac.herts.orchestrator.util;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.config.HardhatProperties;
import uk.ac.herts.orchestrator.exception.SubmissionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HardhatConnector {

    private final Web3j web3j;
    private final HardhatProperties hardhatProperties;

    public record SubmissionResult(EthSendTransaction transaction, int retries) {
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

        int retry = 0;
        int maxRetries = hardhatProperties.getMaxRetries();
        Duration delay = hardhatProperties.getRetryBackoff();

        while (true) {
            try {
                log.debug("Transaction submission attempt {}/{} (retry={})", retry + 1, maxRetries + 1, retry);
                EthSendTransaction result = web3j.ethSendRawTransaction(signedHexPayload).send();
                if (result.hasError()) {
                    String errorMsg = result.getError().getMessage();
                    throw new IllegalStateException(
                            String.format("Transaction submission error on retry %d: %s", retry, errorMsg));
                }
                log.info("Transaction submitted successfully (retry={}). Hash: {}",
                        retry, result.getTransactionHash());
                return new SubmissionResult(result, retry);
            } catch (Exception e) {
                if (retry >= maxRetries) {
                    throw new SubmissionException(
                            String.format("Submission failed after %d retries", maxRetries), maxRetries, e);
                }

                log.debug("Submission attempt {} failed, retrying in {} ms", retry + 1, delay.toMillis(), e);
                sleep(delay);
                delay = delay.multipliedBy(2);
                retry++;
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
