package io.openaev.database.repository;

import io.openaev.database.model.FindingTriageHistory;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FindingTriageHistoryRepository
    extends JpaRepository<FindingTriageHistory, String>,
        JpaSpecificationExecutor<FindingTriageHistory> {

  // Chronological (oldest first) - the endpoint reads as a timeline.
  List<FindingTriageHistory> findByFinding_IdOrderByCreationDateAsc(@NotNull String findingId);
}
