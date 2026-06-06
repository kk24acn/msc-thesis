package uk.ac.herts.orchestrator.exception.mpc;

import java.util.Set;

public class QuorumException extends SignatureGenerationException {

    public QuorumException(int availableNodes, int threshold, Set<Integer> quarantinedParties) {
        super(String.format("Cannot form quorum: only %d nodes available, need %d. Quarantined: %s", availableNodes,
                threshold, quarantinedParties), 0, null, quarantinedParties);
    }
}
