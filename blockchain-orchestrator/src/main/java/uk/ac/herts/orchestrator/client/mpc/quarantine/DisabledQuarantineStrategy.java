package uk.ac.herts.orchestrator.client.mpc.quarantine;

import java.util.List;
import java.util.Set;

public class DisabledQuarantineStrategy implements QuarantineStrategy {

    @Override
    public boolean quarantine(int accusedPartyId, int accuserPartyId, String reason, int totalNodes, int threshold) {
        return false;
    }

    @Override
    public boolean evictExpired() {
        return false;
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
        return Set.of();
    }
}
