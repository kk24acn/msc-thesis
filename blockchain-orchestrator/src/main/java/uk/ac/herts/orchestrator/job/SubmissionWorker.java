package uk.ac.herts.orchestrator.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.ac.herts.orchestrator.client.blockchain.BlockchainClient;
import uk.ac.herts.orchestrator.exception.blockchain.BlockchainRpcException;
import uk.ac.herts.orchestrator.repository.dao.TransactionDao;
import uk.ac.herts.orchestrator.repository.entity.Transaction;
import uk.ac.herts.orchestrator.repository.model.TransactionStatus;
import uk.ac.herts.orchestrator.util.ErrorUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionWorker {

    private final BlockchainClient blockchainClient;
    private final TransactionDao transactionDao;

    @Scheduled(fixedDelayString = "${spring.hardhat.submission-worker.interval-ms:1000}")
    public void pollAndSubmit() {
        try {
            List<Transaction> signedTransactions = transactionDao
                    .findTransactionsByStatusOrderedByNonce(TransactionStatus.SIGNED);

            if (signedTransactions.isEmpty()) {
                return;
            }

            Map<String, List<Transaction>> txsByAddress = signedTransactions.stream()
                    .collect(Collectors.groupingBy(Transaction::getFromAddress));

            for (Map.Entry<String, List<Transaction>> entry : txsByAddress.entrySet()) {
                List<Transaction> addressTxs = entry.getValue();

                for (Transaction tx : addressTxs) {
                    submitTransaction(tx);
                }
            }
        } catch (Exception e) {
            log.error("Error in submission worker polling cycle", e);
        }
    }

    private void submitTransaction(Transaction transaction) {
        if (transaction.getTraceId() != null) {
            MDC.put("traceId", transaction.getTraceId().toString());
        }

        log.info("Submitting transaction id={}, nonce={}, from={}",
                transaction.getId(), transaction.getNonce(), transaction.getFromAddress());

        Transaction submittingTx = transactionDao.markSubmitting(transaction);
        try {
            BlockchainClient.SubmissionResult submissionResult = blockchainClient
                    .submitRawTransaction(submittingTx.getSignedHexPayload(), submittingTx.getFromAddress());

            long submissionBlock = blockchainClient.fetchCurrentBlockNumber();

            transactionDao.markInMempool(submittingTx, submissionResult.transactionHash(), submissionBlock,
                    submissionResult.retries());
            log.info("Successfully submitted transaction id={}", submittingTx.getId());
        } catch (BlockchainRpcException e) {
            log.error("Blockchain RPC error submitting transaction id={}", submittingTx.getId(), e);
            transactionDao.markFailed(submittingTx, ErrorUtils.buildErrorMessage("Submission phase failed", e),
                    e.getRetries());
        } catch (Exception e) {
            log.error("Unexpected error submitting transaction id={}", submittingTx.getId(), e);
            transactionDao.markFailed(submittingTx, ErrorUtils.buildErrorMessage("Submission phase failed", e));
        } finally {
            MDC.clear();
        }
    }
}