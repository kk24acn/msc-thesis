package uk.ac.herts.orchestrator.repository.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.web3j.protocol.core.methods.response.TransactionReceipt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.ac.herts.orchestrator.model.TransactionStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "to_address")
    private String toAddress;
    @Column(name = "amount_ether")
    private BigDecimal amountEther;
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    @Column(name = "transaction_hash")
    private String transactionHash;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "retry_count")
    private int retryCount;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Version
    @Column(name = "version")
    private Long version;

    @Transient
    private byte[] signedPayload;

    @Transient
    private TransactionReceipt receipt;

    public void touchUpdatedAt() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}
