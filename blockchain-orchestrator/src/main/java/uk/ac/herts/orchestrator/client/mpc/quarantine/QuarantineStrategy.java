package uk.ac.herts.orchestrator.client.mpc.quarantine;

import java.util.List;
import java.util.Set;

public interface QuarantineStrategy {

    boolean quarantine(int accusedPartyId, int accuserPartyId, String reason, int totalNodes, int threshold);

    boolean evictExpired();

    List<Integer> adjustQuorum(List<Integer> baseQuorum);

    boolean onQuorumSuccess(List<Integer> executedQuorum);

    void onQuorumFailure(List<Integer> executedQuorum);

    Set<Integer> getQuarantinedIds();
}
