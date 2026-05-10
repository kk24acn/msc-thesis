package uk.ac.herts.orchestrator.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;

import uk.ac.herts.orchestrator.repository.dao.TransactionDao;

import java.math.BigInteger;
import java.util.List;

@Service
public class BlockIndexerService {

    private final Web3j web3j;
    private final TransactionDao transactionDao;
    private BigInteger lastProcessedBlock = BigInteger.ZERO;

    public BlockIndexerService(Web3j web3j, TransactionDao transactionDao) {
        this.web3j = web3j;
        this.transactionDao = transactionDao;
    }

    @Scheduled(fixedDelayString = "${spring.hardhat.block-indexer-interval:1000}")
    public void indexBlocks() {
        try {
            BigInteger latestBlock = web3j.ethBlockNumber().send().getBlockNumber();

            if (lastProcessedBlock.equals(BigInteger.ZERO)) {
                lastProcessedBlock = latestBlock.subtract(BigInteger.ONE);
            }

            while (lastProcessedBlock.compareTo(latestBlock) < 0) {
                BigInteger targetBlock = lastProcessedBlock.add(BigInteger.ONE);
                processBlock(targetBlock);
                lastProcessedBlock = targetBlock;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void processBlock(BigInteger blockNumber) throws Exception {
        EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), true)
                .send()
                .getBlock();

        if (block == null || block.getTransactions().isEmpty()) {
            return;
        }

        List<String> hashes = block.getTransactions().stream()
                .map(tx -> ((EthBlock.TransactionObject) tx).getHash())
                .toList();

        transactionDao.confirmTransactions(hashes, blockNumber.longValue());
    }
}