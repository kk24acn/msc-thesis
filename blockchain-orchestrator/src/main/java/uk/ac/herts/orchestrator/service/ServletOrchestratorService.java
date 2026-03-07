package uk.ac.herts.orchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.utils.Convert;
import signer.SignerGrpc;
import signer.SignerOuterClass;
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

    private final SignerGrpc.SignerBlockingStub signerBlockingStub;

    public ServletOrchestratorService(Web3j web3j,
                                      Credentials credentials,
                                      ServletTransactionDao transactionDao,
                                      HardhatProperties hardhatProperties,
                                      SignerGrpc.SignerBlockingStub signerBlockingStub) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.transactionDao = transactionDao;
        this.hardhatProperties = hardhatProperties;
        this.signerBlockingStub = signerBlockingStub;
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

    private void validateTransactionRequest(SubmitTransactionRequest request) {
        if (!WalletUtils.isValidAddress(request.toAddress())) {
            log.error("Invalid toAddress format in transaction request: {}", request);
            throw new IllegalArgumentException("Invalid toAddress: expected a valid Ethereum hex address");
        }
        log.info("Transaction request is valid: {}", request);
    }

    private Transaction signTransaction(Transaction transaction) throws InterruptedException, ExecutionException {
        transactionDao.markSigning(transaction);

        CompletableFuture<EthGetTransactionCount> nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING).sendAsync();
        CompletableFuture<EthGasPrice> gasPrice = web3j.ethGasPrice().sendAsync();
        CompletableFuture<EthChainId> chainId = web3j.ethChainId().sendAsync(); //TODO what it returns?
        BigInteger valueWei = Convert.toWei(transaction.getAmountEther(), Convert.Unit.ETHER).toBigIntegerExact();

        SignerOuterClass.TransactionRequest trans = SignerOuterClass.TransactionRequest.newBuilder()
                                                        .setNonce(nonce.get().getTransactionCount().toString())
                                                        .setGasPrice(gasPrice.get().getGasPrice().toString())
                                                        .setFrom("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
                                                        .setTo(transaction.getToAddress())
                                                        .setValue(valueWei.toString())
//                                                        .setData("abc")
                                                        .setChainId(31337) // TODO hardcoded
                                                        .build();
        SignerOuterClass.TransactionResponse response = signerBlockingStub.signTransaction(trans);
        return transactionDao.markSigned(transaction, response.getRawTxHex());
    }

    private Transaction submitTransactionOnChain(Transaction transaction) {
        transactionDao.markSubmitting(transaction);

        int attempt = 0;
        int maxRetries = hardhatProperties.getMaxRetries();
        Duration delay = hardhatProperties.getRetryBackoff();
        Throwable lastError = null;
        String payload = transaction.getSignedHexPayload();
        log.info("Transaction payload: {}", payload);

        while (attempt <= maxRetries) {
            try {
                EthSendTransaction transactionResult = web3j.ethSendRawTransaction(payload).send();
                if (transactionResult.hasError()) {
                    transactionDao.markFailed(transaction, transactionResult.getError().getMessage());
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
                transactionDao.markSubmitting(transaction, true);
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
