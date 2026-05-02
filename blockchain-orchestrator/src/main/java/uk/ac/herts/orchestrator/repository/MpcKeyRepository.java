package uk.ac.herts.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;

import java.util.Optional;

@Repository
public interface MpcKeyRepository extends JpaRepository<MpcKey, String> {
    Optional<MpcKey> findByEthereumAddress(String ethereumAddress);
}