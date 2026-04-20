package uk.ac.herts.orchestrator.util;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

@Component
public class NonceManager {
    private final Web3j web3j;
    private final ConcurrentHashMap<String, AtomicBigInteger> nonceCache = new ConcurrentHashMap<>();

    public NonceManager(Web3j web3j) {
        this.web3j = web3j;
    }

    public synchronized BigInteger getCurrentNonce(String address) {
        return nonceCache.computeIfAbsent(address, this::getNonceFromChain).get();
    }

    public synchronized void incrementNonce(String address) {
        nonceCache.computeIfAbsent(address, this::getNonceFromChain).add(BigInteger.ONE);
    }

    private AtomicBigInteger getNonceFromChain(String address) {
        try {
            BigInteger count = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
            return new AtomicBigInteger(count);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch initial nonce for " + address, e);
        }
    }

    private record AtomicBigInteger(AtomicReference<BigInteger> value) {
        private AtomicBigInteger(BigInteger value) {
            this(new AtomicReference<>(value));
        }

        public BigInteger get() {
            return value.get();
        }

        public void add(BigInteger delta) {
            value.getAndUpdate(v -> v.add(delta));
        }
    }
}