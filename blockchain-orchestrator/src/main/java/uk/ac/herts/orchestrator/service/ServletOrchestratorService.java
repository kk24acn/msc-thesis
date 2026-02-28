package uk.ac.herts.orchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionRequest;
import uk.ac.herts.orchestrator.api.dto.SubmitTransactionResponse;
import uk.ac.herts.orchestrator.config.HardhatProperties;
import uk.ac.herts.orchestrator.model.TransactionStatus;
import uk.ac.herts.orchestrator.repository.dao.ServletTransactionDao;
import uk.ac.herts.orchestrator.repository.entity.Transaction;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@Profile("servlet")
public class ServletOrchestratorService {

    private final Web3j web3j;
    private final Credentials credentials;
    private final HardhatProperties hardhatProperties;
    private final ServletTransactionDao transactionDao;

    public ServletOrchestratorService(Web3j web3j,
                                      Credentials credentials,
                                      ServletTransactionDao transactionDao,
                                      HardhatProperties hardhatProperties) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.transactionDao = transactionDao;
        this.hardhatProperties = hardhatProperties;
    }

    public SubmitTransactionResponse startTransaction(SubmitTransactionRequest request) {
        validateTransactionRequest(request);
        Transaction transaction = transactionDao.createTransaction(request.toAddress(), request.amountEther());

        try {
            transaction = processTransaction(transaction);
        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return new SubmitTransactionResponse(transaction.getId(), transaction.getTransactionHash(),
            transaction.getToAddress(), transaction.getAmountEther(), transaction.getStatus().toString(),
            transaction.getErrorMessage(), transaction.getUpdatedAt());
    }

    private Transaction processTransaction(Transaction transaction)
        throws InterruptedException, ExecutionException {
        // Transaction transaction = repository.findById(transactionId)
        // .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        // if (transaction.getStatus().isFinished()) {
        // return transaction;
        // }

        // Transaction was submitted but not finalized
        if (transaction.getStatus() == TransactionStatus.SUBMITTING && transaction.getTransactionHash() != null) {

        }

        transaction = finalizeTransaction(submitTransactionOnChain(signTransaction(transaction)));
        log.info("Transaction processed. Receipt: {}", transaction.getReceipt());
        return transaction;
    }

    private SubmitTransactionRequest validateTransactionRequest(SubmitTransactionRequest request) {
        if (!WalletUtils.isValidAddress(request.toAddress())) {
            log.error("Invalid toAddress format in transaction request: {}", request);
            throw new IllegalArgumentException("Invalid toAddress: expected a valid Ethereum hex address");
        }
        log.info("Transaction request is valid: {}", request);
        return request;
    }

    private Transaction signTransaction(Transaction transaction) throws InterruptedException, ExecutionException {
        transactionDao.markSigning(transaction);

        CompletableFuture<EthGetTransactionCount> nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING).sendAsync();
        CompletableFuture<EthGasPrice> gasPrice = web3j.ethGasPrice().sendAsync();
        CompletableFuture<EthChainId> chainId = web3j.ethChainId().sendAsync();

        BigInteger valueWei = Convert.toWei(transaction.getAmountEther(), Convert.Unit.ETHER).toBigIntegerExact();
        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
            nonce.get().getTransactionCount(),
            gasPrice.get().getGasPrice(),
            hardhatProperties.getGasLimit(),
            transaction.getToAddress(),
            valueWei);

        log.info("Chain ID: {}", chainId.get().getId());
        log.info("Transaction type: {}", rawTransaction.getType());
        transaction.setSignedPayload(TransactionEncoder.signMessage(rawTransaction,
//            chainId.get().getId(), why it returns 2?
            31337,
            credentials));
        return transaction;
    }

    private Transaction submitTransactionOnChain(Transaction transaction) {
        transactionDao.markSubmitting(transaction);

        int attempt = 0;
        int maxRetries = hardhatProperties.getMaxRetries();
        Duration delay = hardhatProperties.getRetryBackoff();
        Throwable lastError = null;
        String payload = Numeric.toHexString(transaction.getSignedPayload());

        while (attempt <= maxRetries) {
            try {
                EthSendTransaction transactionResult = web3j.ethSendRawTransaction(payload).send();
                if (transactionResult.hasError()) {
                    throw new IllegalStateException("Failed to submit transaction: " + transactionResult.getError());
                }
                return transactionDao.markSubmitted(transaction, transactionResult);
            } catch (Throwable e) {
                lastError = e;

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }

                delay = delay.multipliedBy(2);
                attempt++;
            }
        }
        throw new RuntimeException(String.format("Failed to send transaction after {} retries", maxRetries), lastError);
    }

    private Transaction finalizeTransaction(Transaction transaction) {
        // if (transaction.getTransactionHash() == null) {
        // throw new IllegalStateException(
        // "Transaction hash missing in finalizing state. Transaction: " + transaction);
        // }
        Duration pollInterval = hardhatProperties.getReceiptPollInterval();
        long deadlineNanos = System.nanoTime() + hardhatProperties.getTransactionTimeout().toNanos();

        while (System.nanoTime() < deadlineNanos) {
            try {
                Optional<TransactionReceipt> receipt = web3j.ethGetTransactionReceipt(transaction.getTransactionHash())
                                                           .send()
                                                           .getTransactionReceipt();
                if (receipt.isPresent()) {
                    transaction.setReceipt(receipt.get());
                    return transaction;
                }

                Thread.sleep(pollInterval);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", ie);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch receipt", e);
            }
        }
        throw new RuntimeException(String.format("Timed out waiting for receipt. Transaction hash: {} ",
            transaction.getTransactionHash()));
    }

    // TODO How to get which exceptions are recoverable?
    // private boolean isRetryableException(Throwable throwable) {
    // return throwable instanceof TimeoutException
    // || throwable instanceof SocketTimeoutException
    // || throwable instanceof ConnectException
    // || throwable instanceof ClientConnectionException
    // || throwable instanceof IOException;
    // }

}
