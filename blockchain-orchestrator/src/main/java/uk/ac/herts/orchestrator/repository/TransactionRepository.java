package uk.ac.herts.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.ac.herts.orchestrator.model.TransactionStatus;
import uk.ac.herts.orchestrator.repository.entity.Transaction;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByStatusIn(List<TransactionStatus> statuses);
}
