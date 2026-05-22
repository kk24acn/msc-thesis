package uk.ac.herts.orchestrator.client.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.client.blockchain.config.HardhatProperties;
import uk.ac.herts.orchestrator.exception.BlockchainRpcException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HardhatBlockchainClient implements BlockchainClient {

    private final Web3j web3j;
    private final HardhatProperties hardhatProperties;

    @Override
    public BigInteger fetchGasPrice() {
        try {
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            log.debug("Gas price fetched: {} wei", gasPrice);
            return gasPrice;
        } catch (IOException e) {
            throw new RuntimeException("Gas price fetch failed", e);
        }
    }

    @Override
    public long fetchCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber().longValue();
        } catch (IOException e) {
            throw new RuntimeException("Block number fetch failed", e);
        }
    }

    @Override
    public BigInteger getGasLimit() {
        return BigInteger.valueOf(hardhatProperties.getGasLimit());
    }

    @Override
    public long fetchPendingTransactionCount(String address) {
        try {
            BigInteger count = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
            return count.longValue();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to fetch nonce for %s", address), e);
        }
    }

    @Override
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
                return new SubmissionResult(result.getTransactionHash(), retry);
            } catch (Exception e) {
                if (retry >= maxRetries) {
                    throw new BlockchainRpcException(
                            String.format("Submission failed after %d retries", maxRetries), maxRetries, e);
                }

                log.debug("Submission attempt {} failed, retrying in {} ms", retry + 1, delay.toMillis(), e);
                sleep(delay);
                delay = delay.multipliedBy(2);
                retry++;
            }
        }
    }

    @Override
    public List<String> fetchBlockTransactionHashes(BigInteger blockNumber) {
        try {
            EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), true)
                    .send()
                    .getBlock();

            if (block == null || block.getTransactions().isEmpty()) {
                return Collections.emptyList();
            }

            return block.getTransactions().stream()
                    .map(tx -> ((EthBlock.TransactionObject) tx).getHash())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to fetch block %s", blockNumber), e);
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
