package uk.ac.herts.orchestrator.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SubmitTransactionRequest(
    @NotBlank String keyId,
    @NotBlank String toAddress,
    @NotNull @DecimalMin(value = "0.000000000000000001") BigDecimal amountEther
) {
}
