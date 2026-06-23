package uk.ac.herts.orchestrator.client.mpc;

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
import uk.ac.herts.orchestrator.client.mpc.quarantine.QuarantineStrategy;
import uk.ac.herts.orchestrator.exception.mpc.DsgRoundException;
import uk.ac.herts.orchestrator.exception.mpc.QuorumException;

@Slf4j
@Component
public class QuorumManager {

    private static final Pattern BAN_PATTERN = Pattern.compile("ban\\s+(?:the\\s+)?party\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final MpcProperties mpcProperties;
    private final QuarantineStrategy quarantineStrategy;
    private final AtomicLong lastEvictionTimeMs = new AtomicLong(0);
    private final ReentrantLock snapshotLock = new ReentrantLock();

    private volatile Set<Integer> allNodeIds = Set.of();
    private volatile QuorumSnapshot snapshot = QuorumSnapshot.empty();

    public QuorumManager(MpcProperties mpcProperties, QuarantineStrategy quarantineStrategy) {
        this.mpcProperties = mpcProperties;
        this.quarantineStrategy = quarantineStrategy;
    }

    private record QuorumSnapshot(List<Integer> availableNodes, Set<Integer> quarantinedIds,
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
        log.info("QuorumManager initialized with {} nodes: {} (mode: {})",
                nodeIds.size(), nodeIds, mpcProperties.getQuarantine().getMode());
    }

    public List<Integer> selectQuorum(int threshold, int roundRobinIndex) {
        evictExpired();
        QuorumSnapshot snap = this.snapshot;

        if (snap.availableNodes().size() < threshold) {
            throw new QuorumException(snap.availableNodes().size(), threshold, snap.quarantinedIds());
        }

        List<List<Integer>> combinations = snap.combinationsByThreshold().computeIfAbsent(
                threshold, t -> generateCombinations(snap.availableNodes(), t));

        List<Integer> baseQuorum = combinations.get(roundRobinIndex % combinations.size());
        return quarantineStrategy.adjustQuorum(baseQuorum);
    }

    public Set<Integer> getQuarantinedPartyIds() {
        return snapshot.quarantinedIds();
    }

    public boolean processBanRequest(Throwable exception, int threshold) {
        Optional<Integer> accusedPartyId = parseAccusedPartyFromChain(exception);
        int failedPartyId = DsgRoundException.extractReportingPartyId(exception);

        int accused;
        int accuserPartyId;
        String reason;

        if (accusedPartyId.isPresent()) {
            accused = accusedPartyId.get();
            accuserPartyId = failedPartyId;
            reason = DsgRoundException.extractGrpcDescription(exception);
        } else if (failedPartyId != -1) {
            accused = failedPartyId;
            accuserPartyId = -1;
            reason = "Node unresponsive: " + DsgRoundException.extractGrpcDescription(exception);
        } else {
            return false;
        }

        return tryQuarantine(accused, accuserPartyId, reason, threshold);
    }

    public void onQuorumSuccess(List<Integer> quorum) {
        if (quarantineStrategy.onQuorumSuccess(quorum)) {
            rebuildSnapshot();
        }
    }

    public void onQuorumFailure(List<Integer> quorum) {
        quarantineStrategy.onQuorumFailure(quorum);
    }

    private boolean tryQuarantine(int accusedPartyId, int accuserPartyId, String reason, int threshold) {
        snapshotLock.lock();
        try {
            boolean quarantined = quarantineStrategy.quarantine(accusedPartyId, accuserPartyId, reason,
                    allNodeIds.size(), threshold);
            if (quarantined) {
                rebuildSnapshot();
            }
            return quarantined;
        } finally {
            snapshotLock.unlock();
        }
    }

    private void rebuildSnapshot() {
        Set<Integer> quarantined = quarantineStrategy.getQuarantinedIds();
        List<Integer> available = allNodeIds.stream()
                .filter(id -> !quarantined.contains(id))
                .sorted()
                .toList();
        this.snapshot = QuorumSnapshot.of(available, quarantined);
    }

    private void evictExpired() {
        if (snapshot.quarantinedIds().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastCheck = lastEvictionTimeMs.get();
        if (now - lastCheck > mpcProperties.getQuarantine().getEvictionIntervalMs()) {
            if (lastEvictionTimeMs.compareAndSet(lastCheck, now)) {
                snapshotLock.lock();
                try {
                    if (quarantineStrategy.evictExpired()) {
                        rebuildSnapshot();
                    }
                } finally {
                    snapshotLock.unlock();
                }
            }
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
