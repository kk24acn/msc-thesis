package uk.ac.herts.orchestrator.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.crypto.RawTransaction;
import uk.ac.herts.orchestrator.client.blockchain.BlockchainClient;
import uk.ac.herts.orchestrator.client.mpc.TransactionSigner;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;
import uk.ac.herts.orchestrator.repository.entity.Transaction;

import java.math.BigInteger;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mpc.nonce-gap-sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class NonceGapSweeper {

    private final TransactionDao transactionDao;
    private final MpcKeyRepository mpcKeyRepository;
    private final BlockchainClient blockchainClient;
    private final TransactionSigner transactionSigner;

    @Scheduled(fixedDelayString = "${spring.mpc.nonce-gap-sweeper.interval-ms:5000}")
    public void fillNonceGaps() {
        List<Transaction> failedTransactions = transactionDao.findFailedTransactionsOrderedByNonce();

        if (failedTransactions.isEmpty()) {
            return;
        }

        log.info("Sweeper detected {} failed transactions. Initiating gap-fillers", failedTransactions.size());

        BigInteger gasPrice;
        BigInteger gasLimit;
        try {
            gasPrice = blockchainClient.fetchGasPrice();
            gasLimit = blockchainClient.getGasLimit();
        } catch (Exception e) {
            log.error("Failed to fetch gas price/limit for sweeper", e);
            return;
        }

        for (Transaction tx : failedTransactions) {
            try {
                transactionDao.incrementSweeperAttempts(tx);

                MpcKey mpcKey = mpcKeyRepository.findByEthereumAddress(tx.getFromAddress())
                        .orElseThrow(
                                () -> new IllegalStateException("Key not found for address " + tx.getFromAddress()));

                RawTransaction rawTx = RawTransaction.createEtherTransaction(
                        BigInteger.valueOf(tx.getNonce()),
                        gasPrice,
                        gasLimit,
                        tx.getToAddress(),
                        BigInteger.ZERO);

                TransactionSigner.SignResult signResult = transactionSigner.sign(rawTx, tx, mpcKey);
                transactionDao.markSigned(tx, signResult.hexPayload(), signResult.retries());
                log.info("Successfully signed gap-filler for transaction {} at nonce {}", tx.getId(), tx.getNonce());
            } catch (Exception e) {
                log.warn("Sweeper failed to sign gap-filler for transaction {} at nonce {}. Will retry next cycle",
                        tx.getId(), tx.getNonce(), e);
            }
        }
    }
}