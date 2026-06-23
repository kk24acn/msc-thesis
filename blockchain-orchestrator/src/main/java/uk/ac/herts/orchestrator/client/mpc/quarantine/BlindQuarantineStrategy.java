package uk.ac.herts.orchestrator.client.mpc.quarantine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.client.mpc.config.MpcProperties;

@Slf4j
public class BlindQuarantineStrategy implements QuarantineStrategy {

    protected final MpcProperties.QuarantineProperties config;
    protected final ConcurrentHashMap<Integer, QuarantineEntry> quarantinedNodes = new ConcurrentHashMap<>();
    private final boolean allowThresholdViolation;

    public BlindQuarantineStrategy(MpcProperties.QuarantineProperties config, boolean allowThresholdViolation) {
        this.config = config;
        this.allowThresholdViolation = allowThresholdViolation;
    }

    @Override
    public boolean quarantine(int accusedPartyId, int accuserPartyId, String reason, int totalNodes, int threshold) {
        if (quarantinedNodes.containsKey(accusedPartyId)) {
            log.info("Party {} is already quarantined", accusedPartyId);
            return false;
        }

        int currentlyAvailable = totalNodes - quarantinedNodes.size();
        if (currentlyAvailable - 1 < threshold) {
            if (!allowThresholdViolation) {
                log.error("REFUSING to quarantine party {}: would leave {} available nodes, below threshold {}. "
                        + "Accuser: party {}, reason: {}", accusedPartyId, currentlyAvailable - 1, threshold,
                        accuserPartyId, reason);
                return false;
            }
            log.warn("Quarantining party {} despite leaving {} available nodes (below threshold {}). "
                    + "Accuser: party {}, reason: {}", accusedPartyId, currentlyAvailable - 1, threshold,
                    accuserPartyId, reason);
        }

        Duration ttl = config.getTtl();
        Instant now = Instant.now();
        quarantinedNodes.put(accusedPartyId,
                new QuarantineEntry(accusedPartyId, accuserPartyId, reason, now, now.plus(ttl)));

        log.warn("QUARANTINED party {} (accused by party {}). Reason: {}. TTL: {}", accusedPartyId, accuserPartyId,
                reason, ttl);

        return true;
    }

    @Override
    public boolean evictExpired() {
        Instant now = Instant.now();
        return quarantinedNodes.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt().isBefore(now)) {
                log.info("Quarantine expired for party {} (quarantined at {}, accused by party {})",
                        entry.getKey(),
                        entry.getValue().quarantinedAt(),
                        entry.getValue().accuserPartyId());
                onEvict(entry.getKey());
                return true;
            }
            return false;
        });
    }

    protected void onEvict(int partyId) {
    }

    @Override
    public List<Integer> adjustQuorum(List<Integer> baseQuorum) {
        return baseQuorum;
    }

    @Override
    public boolean onQuorumSuccess(List<Integer> executedQuorum) {
        return false;
    }

    @Override
    public void onQuorumFailure(List<Integer> executedQuorum) {
    }

    @Override
    public Set<Integer> getQuarantinedIds() {
        return Set.copyOf(quarantinedNodes.keySet());
    }
}
