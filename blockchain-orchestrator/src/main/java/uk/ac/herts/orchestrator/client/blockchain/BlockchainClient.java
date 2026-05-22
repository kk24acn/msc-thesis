package uk.ac.herts.orchestrator.client.blockchain;

import java.math.BigInteger;
import java.util.List;

public interface BlockchainClient {

    public record SubmissionResult(String transactionHash, int retries) {
    }

    BigInteger fetchGasPrice();

    long fetchCurrentBlockNumber();

    BigInteger getGasLimit();

    long fetchPendingTransactionCount(String address);

    SubmissionResult submitRawTransaction(String signedHexPayload, String fromAddress);

    List<String> fetchBlockTransactionHashes(BigInteger blockNumber);
}
