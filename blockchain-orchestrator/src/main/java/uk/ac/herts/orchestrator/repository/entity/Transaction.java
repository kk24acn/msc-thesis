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

    @Column(name = "from_address", length = 50)
    private String fromAddress;

    @Column(name = "to_address", length = 50)
    private String toAddress;

    @Column(name = "amount_ether")
    private BigDecimal amountEther;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "hash", length = 100)
    private String hash;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "signing_retries")
    private Integer signingRetries;

    @Column(name = "submission_retries")
    private Integer submissionRetries;

    @Column(name = "nonce")
    private Long nonce;

    @Column(name = "submission_block")
    private Long submissionBlock;

    @Column(name = "mined_block")
    private Long minedBlock;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "signing_started_at")
    private OffsetDateTime signingStartedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "trace_id", length = 64)
    private String traceId;

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
}
