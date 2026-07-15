package uk.ac.herts.orchestrator.client.mpc.quarantine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.client.mpc.config.MpcProperties;

@Slf4j
public class CircuitBreakerQuarantineStrategy extends BlindQuarantineStrategy {

    private final ConcurrentHashMap<Integer, AtomicInteger> probeCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
    private final Set<Integer> activeProbes = ConcurrentHashMap.newKeySet();
    private final int probeInterval;
    private final int failureThreshold;

    public CircuitBreakerQuarantineStrategy(MpcProperties.QuarantineProperties config,
            boolean allowThresholdViolation) {
        super(config, allowThresholdViolation);
        this.probeInterval = config.getProbeInterval();
        this.failureThreshold = config.getFailureThreshold();
    }

    @Override
    public boolean quarantine(int accusedPartyId, int accuserPartyId, String reason, int totalNodes, int threshold) {
        boolean isByzantine = accuserPartyId >= 0;

        int currentFailures = failureCounters.computeIfAbsent(accusedPartyId,
                k -> new AtomicInteger(0)).incrementAndGet();

        if (isByzantine || currentFailures >= failureThreshold) {
            boolean quarantined = super.quarantine(accusedPartyId, accuserPartyId, reason, totalNodes, threshold);
            if (quarantined) {
                probeCounters.put(accusedPartyId, new AtomicInteger(0));
                failureCounters.get(accusedPartyId).set(0);
            }
            return quarantined;
        }

        log.info("Circuit Breaker absorbed transient failure for party {}. ({}/{} consecutive failures)",
                accusedPartyId, currentFailures, failureThreshold);
        return false;
    }

    @Override
    protected void onEvict(int partyId) {
        probeCounters.remove(partyId);
        activeProbes.remove(partyId);
        failureCounters.remove(partyId);
    }

    @Override
    public List<Integer> adjustQuorum(List<Integer> baseQuorum) {
        Optional<Integer> probeTarget = selectProbeTarget();
        if (probeTarget.isEmpty() || baseQuorum.isEmpty()) {
            return baseQuorum;
        }

        Integer probePartyId = probeTarget.get();
        if (baseQuorum.contains(probePartyId)) {
            return baseQuorum;
        }

        List<Integer> adjusted = new ArrayList<>(baseQuorum);
        int lastIndex = adjusted.size() - 1;
        log.info("Injecting probe party {} into quorum (replacing party {})",
                probePartyId, adjusted.get(lastIndex));
        adjusted.set(lastIndex, probePartyId);

        return List.copyOf(adjusted);
    }

    @Override
    public boolean onQuorumSuccess(List<Integer> executedQuorum) {
        boolean stateChanged = false;

        for (Integer partyId : executedQuorum) {
            AtomicInteger failureCounter = failureCounters.get(partyId);
            if (failureCounter != null) {
                failureCounter.set(0);
            }

            if (!activeProbes.remove(partyId)) {
                continue;
            }
            QuarantineEntry entry = quarantinedNodes.remove(partyId);
            probeCounters.remove(partyId);
            if (entry != null) {
                Duration saved = Duration.between(Instant.now(), entry.expiresAt());
                log.info("Probe SUCCEEDED for party {}. Quarantine lifted early "
                        + "(quarantined at {}, would expire at {}). Time saved: {}",
                        partyId, entry.quarantinedAt(), entry.expiresAt(), saved);
                stateChanged = true;
            }
        }
        return stateChanged;
    }

    @Override
    public void onQuorumFailure(List<Integer> executedQuorum) {
        Instant newExpiry = Instant.now().plus(config.getTtl());
        for (Integer partyId : executedQuorum) {
            if (!activeProbes.remove(partyId)) {
                continue;
            }
            AtomicInteger counter = probeCounters.get(partyId);
            if (counter != null) {
                counter.set(0);
            }
            quarantinedNodes.computeIfPresent(partyId, (id, entry) ->
                    new QuarantineEntry(id, entry.accuserPartyId(), entry.reason(), entry.quarantinedAt(), newExpiry));
            log.warn("Probe FAILED for party {}. Quarantine TTL extended by {}. Counter reset, next probe in {} transactions",
                    partyId, config.getTtl(), probeInterval);
        }
    }

    private Optional<Integer> selectProbeTarget() {
        Optional<Integer> target = Optional.empty();
        for (Integer partyId : quarantinedNodes.keySet()) {
            if (activeProbes.contains(partyId)) {
                continue;
            }
            AtomicInteger counter = probeCounters.get(partyId);
            if (counter == null) {
                continue;
            }
            int count = counter.incrementAndGet();
            if (count >= probeInterval && target.isEmpty()) {
                counter.set(0);
                activeProbes.add(partyId);
                log.info("Party {} selected for probe (counter reached {}/{})",
                        partyId, count, probeInterval);
                target = Optional.of(partyId);
            }
        }
        return target;
    }
}
