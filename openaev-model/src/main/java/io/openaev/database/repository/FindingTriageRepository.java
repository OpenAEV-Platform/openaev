package io.openaev.database.repository;

import io.openaev.database.model.FindingTriage;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FindingTriageRepository
    extends JpaRepository<FindingTriage, String>, JpaSpecificationExecutor<FindingTriage> {

  Optional<FindingTriage> findByFinding_Id(@NotNull String findingId);

  Optional<FindingTriage> findByFinding_IdAndTenantId(
      @NotNull String findingId, @NotNull String tenantId);

  // Bulk lookup - used by FindingMapper's callers (FindingDistinctSearchService,
  // FindingSearchApi) to fetch triage statuses for a whole page of findings in a single query,
  // avoiding N+1 (one findByFinding_Id call per finding in a loop).
  List<FindingTriage> findByFinding_IdIn(@NotNull List<String> findingIds);
}
