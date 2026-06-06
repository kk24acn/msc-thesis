package uk.ac.herts.orchestrator.exception.blockchain;

public class BlockchainRpcException extends RuntimeException {
    private final int retries;

    public BlockchainRpcException(String message, int retries, Throwable cause) {
        super(message, cause);
        this.retries = retries;
    }

    public int getRetries() {
        return retries;
    }
}
