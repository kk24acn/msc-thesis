package uk.ac.herts.orchestrator.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mpc_keys")
@Getter
@Setter
@NoArgsConstructor
public class MpcKey {

    @Id
    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "ethereum_address", nullable = false, unique = true)
    private String ethereumAddress;

    @Column(name = "threshold", nullable = false)
    private Integer threshold;

    @Column(name = "total_parties", nullable = false)
    private Integer totalParties;

    @Column(name = "derivation_path", nullable = false)
    private String derivationPath;
}