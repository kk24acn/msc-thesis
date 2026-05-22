package uk.ac.herts.orchestrator.client.blockchain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.ac.herts.orchestrator.repository.MpcKeyRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class NonceManager {

    private final BlockchainClient blockchainClient;
    private final MpcKeyRepository mpcKeyRepository;

    private final ConcurrentHashMap<String, AtomicLong> nonceCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        mpcKeyRepository.findAll().forEach(key -> {
            String address = key.getEthereumAddress();
            nonceCache.put(address, new AtomicLong(blockchainClient.fetchPendingTransactionCount(address)));
        });
    }

    public long getAndIncrementNonce(String address) {
        long nonce = nonceCache
                .computeIfAbsent(address, a -> new AtomicLong(blockchainClient.fetchPendingTransactionCount(a)))
                .getAndIncrement();
        log.debug("Current nonce for address {}: {}", address, nonce);
        return nonce;
    }
}
