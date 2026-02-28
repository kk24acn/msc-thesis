package uk.ac.herts.orchestrator.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SubmitTransactionResponse(
        UUID transactionId,
        String transactionHash,
        String toAddress,
        BigDecimal amountEther,
        String status,
        String errorMessage,
        OffsetDateTime updatedAt
) {
}
