package uk.ac.herts.orchestrator.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import uk.ac.herts.orchestrator.repository.model.TransactionStatus;

import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "to_address", length = 50)
    private String toAddress;

    @Column(name = "amount_ether")
    private BigDecimal amountEther;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "transaction_hash", length = 100)
    private String transactionHash;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "signed_hex_payload")
    private String signedHexPayload;

    @Version
    @Column(name = "version")
    private Long version;

    @Transient
    private TransactionReceipt receipt;

    public void touchUpdatedAt() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}
