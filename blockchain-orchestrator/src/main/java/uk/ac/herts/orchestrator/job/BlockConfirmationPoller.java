package uk.ac.herts.orchestrator.job;

import java.math.BigInteger;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.client.blockchain.BlockchainClient;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlockConfirmationPoller {

    private final BlockchainClient blockchainClient;
    private final TransactionDao transactionDao;
    private BigInteger lastProcessedBlock = BigInteger.ZERO;

    @Scheduled(fixedDelayString = "${spring.hardhat.block-indexer-interval-ms:1000}")
    public void poll() {
        try {
            BigInteger latestBlock = BigInteger.valueOf(blockchainClient.fetchCurrentBlockNumber());

            if (lastProcessedBlock.equals(BigInteger.ZERO)) {
                lastProcessedBlock = latestBlock.subtract(BigInteger.ONE);
            }

            while (lastProcessedBlock.compareTo(latestBlock) < 0) {
                BigInteger targetBlock = lastProcessedBlock.add(BigInteger.ONE);
                List<String> hashes = blockchainClient.fetchBlockTransactionHashes(targetBlock);
                if (!hashes.isEmpty()) {
                    transactionDao.confirmTransactions(hashes, targetBlock.longValue());
                }
                lastProcessedBlock = targetBlock;
            }
        } catch (Exception e) {
            log.warn("Block polling cycle failed at block {}", lastProcessedBlock, e);
        }
    }
}
