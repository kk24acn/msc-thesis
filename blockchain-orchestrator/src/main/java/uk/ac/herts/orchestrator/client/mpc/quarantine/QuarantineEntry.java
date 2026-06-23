package uk.ac.herts.orchestrator.client.mpc.quarantine;

import java.time.Instant;

public record QuarantineEntry(
        int partyId,
        int accuserPartyId,
        String reason,
        Instant quarantinedAt,
        Instant expiresAt) {
}
