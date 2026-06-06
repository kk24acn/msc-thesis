package uk.ac.herts.orchestrator.client.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.client.blockchain.config.HardhatProperties;
import uk.ac.herts.orchestrator.exception.blockchain.BlockchainRpcException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HardhatBlockchainClient implements BlockchainClient {

    private final Web3j web3j;
    private final HardhatProperties hardhatProperties;

    private volatile BigInteger cachedGasPrice;
    private volatile long lastGasPriceFetchTime;
    private final ReentrantLock gasPriceLock = new ReentrantLock();

    private volatile Long cachedBlockNumber;
    private volatile long lastBlockNumberFetchTime;
    private final ReentrantLock blockNumberLock = new ReentrantLock();

    @Override
    public BigInteger fetchGasPrice() {
        long now = System.currentTimeMillis();
        long cacheTtlMs = hardhatProperties.getGasPriceCacheTtl().toMillis();

        if (cachedGasPrice != null && now - lastGasPriceFetchTime < cacheTtlMs) {
            return cachedGasPrice;
        }
        gasPriceLock.lock();
        try {
            if (cachedGasPrice != null && now - lastGasPriceFetchTime < cacheTtlMs) {
                return cachedGasPrice;
            }
            try {
                BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
                log.debug("Gas price fetched: {} wei", gasPrice);
                cachedGasPrice = gasPrice;
                lastGasPriceFetchTime = System.currentTimeMillis();
                return gasPrice;
            } catch (IOException e) {
                throw new BlockchainRpcException("Gas price fetch failed", 0, e);
            }
        } finally {
            gasPriceLock.unlock();
        }
    }

    @Override
    public long fetchCurrentBlockNumber() {
        long now = System.currentTimeMillis();
        long cacheTtlMs = hardhatProperties.getBlockNumberCacheTtl().toMillis();

        if (cachedBlockNumber != null && now - lastBlockNumberFetchTime < cacheTtlMs) {
            return cachedBlockNumber;
        }
        blockNumberLock.lock();
        try {
            if (cachedBlockNumber != null && now - lastBlockNumberFetchTime < cacheTtlMs) {
                return cachedBlockNumber;
            }
            try {
                long blockNumber = web3j.ethBlockNumber().send().getBlockNumber().longValue();
                cachedBlockNumber = blockNumber;
                lastBlockNumberFetchTime = System.currentTimeMillis();
                return blockNumber;
            } catch (IOException e) {
                throw new BlockchainRpcException("Block number fetch failed", 0, e);
            }
        } finally {
            blockNumberLock.unlock();
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
            throw new BlockchainRpcException(String.format("Failed to fetch nonce for %s", address), 0, e);
        }
    }

    @Override
    public long fetchMinedTransactionCount(String address) {
        try {
            BigInteger count = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST)
                    .send().getTransactionCount();
            return count.longValue();
        } catch (IOException e) {
            throw new BlockchainRpcException(String.format("Failed to fetch mined nonce for %s", address), 0, e);
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
                    if (errorMsg != null && (errorMsg.toLowerCase().contains("known transaction")
                            || errorMsg.toLowerCase().contains("already known"))) {
                        String hash = extractHash(errorMsg);
                        log.info("Transaction already in mempool (retry={}). Hash: {}", retry, hash);
                        return new SubmissionResult(hash, retry);
                    }
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
            throw new BlockchainRpcException(String.format("Failed to fetch block %s", blockNumber), 0, e);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractHash(String errorMsg) {
        int index = errorMsg.indexOf("0x");
        if (index != -1) {
            return errorMsg.substring(index).trim();
        }
        return "UNKNOWN_HASH";
    }
}
