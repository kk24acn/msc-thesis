package uk.ac.herts.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.herts.orchestrator.repository.entity.Transaction;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByFromAddressAndNonce(String fromAddress, Long nonce);

    @Modifying
    @Transactional
    @Query(value = "UPDATE transactions SET status = 'CONFIRMED', mined_block = :blockNumber, confirmed_at = :confirmedAt WHERE hash IN (:hashes) AND status = 'IN_MEMPOOL'", nativeQuery = true)
    int confirmTransactions(
            @Param("hashes") List<String> hashes,
            @Param("blockNumber") Long blockNumber,
            @Param("confirmedAt") OffsetDateTime confirmedAt);

    @Query(value = "SELECT DISTINCT ON (ethereum_address) * FROM transactions WHERE status = 'IN_MEMPOOL' ORDER BY ethereum_address, nonce ASC", nativeQuery = true)
    List<Transaction> findLowestNonceInMempoolPerAddress();
}
