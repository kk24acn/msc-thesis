package uk.ac.herts.orchestrator.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record SubmitTransactionResponse(
        UUID transactionId,
        String toAddress,
        BigDecimal amountEther,
        String status) {
}
