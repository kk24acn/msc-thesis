package uk.ac.herts.orchestrator.client.mpc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.client.mpc.config.MpcProperties;
import uk.ac.herts.orchestrator.exception.mpc.DsgRoundException;
import uk.ac.herts.orchestrator.exception.mpc.QuorumException;

@Slf4j
@Component
public class QuorumManager {

    private static final Pattern BAN_PATTERN = Pattern.compile("ban\\s+(?:the\\s+)?party\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final MpcProperties mpcProperties;
    private final AtomicLong lastEvictionTimeMs = new AtomicLong(0);

    private final ConcurrentHashMap<Integer, QuarantineEntry> quarantinedNodes = new ConcurrentHashMap<>();
    private final ReentrantLock quarantineLock = new ReentrantLock();

    private volatile Set<Integer> allNodeIds = Set.of();
    private volatile QuorumSnapshot snapshot = QuorumSnapshot.empty();

    public QuorumManager(MpcProperties mpcProperties) {
        this.mpcProperties = mpcProperties;
    }

    public record QuarantineEntry(
            int partyId,
            int accuserPartyId,
            String reason,
            Instant quarantinedAt,
            Instant expiresAt) {
    }

    private record QuorumSnapshot(
            List<Integer> availableNodes,
            Set<Integer> quarantinedIds,
            ConcurrentHashMap<Integer, List<List<Integer>>> combinationsByThreshold) {

        static QuorumSnapshot empty() {
            return new QuorumSnapshot(List.of(), Set.of(), new ConcurrentHashMap<>());
        }

        static QuorumSnapshot of(List<Integer> available, Set<Integer> quarantined) {
            return new QuorumSnapshot(available, quarantined, new ConcurrentHashMap<>());
        }
    }

    public void initialize(Set<Integer> nodeIds) {
        this.allNodeIds = Set.copyOf(nodeIds);
        rebuildSnapshot();
        log.info("QuorumManager initialized with {} nodes: {}", nodeIds.size(), nodeIds);
    }

    public List<List<Integer>> getAvailableQuorums(int threshold) {
        evictExpired();
        QuorumSnapshot snap = this.snapshot;

        if (snap.availableNodes().size() < threshold) {
            throw new QuorumException(snap.availableNodes().size(), threshold, snap.quarantinedIds());
        }

        return snap.combinationsByThreshold().computeIfAbsent(
                threshold, t -> generateCombinations(snap.availableNodes(), t));
    }

    public Set<Integer> getQuarantinedPartyIds() {
        return snapshot.quarantinedIds();
    }

    public boolean processBanRequest(Throwable exception, int threshold) {
        Optional<Integer> accusedPartyId = parseAccusedPartyFromChain(exception);
        if (accusedPartyId.isEmpty()) {
            return false;
        }

        int accused = accusedPartyId.get();
        int accuserPartyId = DsgRoundException.extractReportingPartyId(exception);
        String reason = DsgRoundException.extractGrpcDescription(exception);

        return tryQuarantine(accused, accuserPartyId, reason, threshold);
    }

    private boolean tryQuarantine(int accusedPartyId, int accuserPartyId, String reason, int threshold) {
        if (!mpcProperties.getQuarantine().isEnabled()) {
            log.debug("Quarantine is disabled, ignoring ban request for party {}", accusedPartyId);
            return false;
        }

        quarantineLock.lock();
        try {
            evictExpiredUnderLock();

            if (quarantinedNodes.containsKey(accusedPartyId)) {
                log.info("Party {} is already quarantined", accusedPartyId);
                return false;
            }

            int currentlyAvailable = allNodeIds.size() - quarantinedNodes.size();
            if (currentlyAvailable - 1 < threshold) {
                log.error(
                        "REFUSING to quarantine party {}: would leave {} available nodes, which is below threshold {}"
                                + " Accuser: party {}, reason: {}",
                        accusedPartyId, currentlyAvailable - 1, threshold, accuserPartyId, reason);
                return false;
            }

            Duration ttl = mpcProperties.getQuarantine().getTtl();
            Instant now = Instant.now();
            quarantinedNodes.put(accusedPartyId, new QuarantineEntry(
                    accusedPartyId, accuserPartyId, reason, now, now.plus(ttl)));

            log.warn("QUARANTINED party {} (accused by party {}). Reason: {}. TTL: {}",
                    accusedPartyId, accuserPartyId, reason, ttl);

            rebuildSnapshot();
            return true;
        } finally {
            quarantineLock.unlock();
        }
    }

    private void rebuildSnapshot() {
        Set<Integer> quarantined = Set.copyOf(quarantinedNodes.keySet());
        List<Integer> available = allNodeIds.stream()
                .filter(id -> !quarantined.contains(id))
                .sorted()
                .toList();
        this.snapshot = QuorumSnapshot.of(available, quarantined);
    }

    private void evictExpired() {
        if (quarantinedNodes.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastCheck = lastEvictionTimeMs.get();
        if (now - lastCheck > mpcProperties.getQuarantine().getEvictionIntervalMs()) {
            if (lastEvictionTimeMs.compareAndSet(lastCheck, now)) {
                quarantineLock.lock();
                try {
                    evictExpiredUnderLock();
                } finally {
                    quarantineLock.unlock();
                }
            }
        }
    }

    private void evictExpiredUnderLock() {
        Instant now = Instant.now();
        boolean anyEvicted = quarantinedNodes.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt().isBefore(now)) {
                log.info("Quarantine expired for party {} (was quarantined at {}, accused by party {})",
                        entry.getKey(),
                        entry.getValue().quarantinedAt(),
                        entry.getValue().accuserPartyId());
                return true;
            }
            return false;
        });
        if (anyEvicted) {
            rebuildSnapshot();
        }
    }

    public Optional<Integer> parseAccusedPartyFromChain(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof StatusRuntimeException e) {
                Optional<Integer> result = parseAccusedParty(e);
                if (result.isPresent()) {
                    return result;
                }
            }
            current = current.getCause();
            depth++;
        }
        return Optional.empty();
    }

    private Optional<Integer> parseAccusedParty(StatusRuntimeException e) {
        String description = e.getStatus().getDescription();
        if (description == null) {
            return Optional.empty();
        }

        Matcher matcher = BAN_PATTERN.matcher(description);
        if (matcher.find()) {
            int accusedPartyId = Integer.parseInt(matcher.group(1));
            log.info("Parsed ban request from gRPC {}: accused party={}", e.getStatus().getCode(), accusedPartyId);
            return Optional.of(accusedPartyId);
        }

        return Optional.empty();
    }

    private List<List<Integer>> generateCombinations(List<Integer> nodes, int threshold) {
        List<List<Integer>> combinations = new ArrayList<>();
        generateCombinationsHelper(nodes, threshold, 0, new ArrayList<>(), combinations);
        return combinations;
    }

    private void generateCombinationsHelper(List<Integer> nodes, int threshold, int start,
            List<Integer> current, List<List<Integer>> combinations) {
        if (current.size() == threshold) {
            combinations.add(List.copyOf(current));
            return;
        }
        for (int i = start; i < nodes.size(); i++) {
            current.add(nodes.get(i));
            generateCombinationsHelper(nodes, threshold, i + 1, current, combinations);
            current.remove(current.size() - 1);
        }
    }
}
