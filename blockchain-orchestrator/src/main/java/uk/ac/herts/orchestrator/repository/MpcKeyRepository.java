package uk.ac.herts.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;

import java.util.Optional;

public interface MpcKeyRepository extends JpaRepository<MpcKey, String> {
    Optional<MpcKey> findByEthereumAddress(String ethereumAddress);
}