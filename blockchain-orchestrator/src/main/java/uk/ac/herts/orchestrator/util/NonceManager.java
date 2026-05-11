package uk.ac.herts.orchestrator.util;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class NonceManager {

    private final Web3j web3j;
    private final MpcKeyRepository mpcKeyRepository;

    private final ConcurrentHashMap<String, AtomicLong> nonceCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        mpcKeyRepository.findAll().forEach(key -> {
            String address = key.getEthereumAddress();
            nonceCache.put(address, new AtomicLong(fetchNonceFromChain(address)));
        });
    }

    public long getAndIncrementNonce(String address) {
        long nonce = nonceCache
                .computeIfAbsent(address, a -> new AtomicLong(fetchNonceFromChain(a)))
                .getAndIncrement();
        log.debug("Current nonce for address {}: {}", address, nonce);
        return nonce;
    }

    private long fetchNonceFromChain(String address) {
        try {
            BigInteger count = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
            return count.longValue();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to fetch nonce for %s", address), e);
        }
    }
}