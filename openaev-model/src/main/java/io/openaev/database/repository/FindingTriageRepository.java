package io.openaev.database.repository;

import io.openaev.database.model.FindingTriage;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FindingTriageRepository
    extends JpaRepository<FindingTriage, String>, JpaSpecificationExecutor<FindingTriage> {

  Optional<FindingTriage> findByFinding_Id(@NotNull String findingId);

  Optional<FindingTriage> findByFinding_IdAndTenantId(
      @NotNull String findingId, @NotNull String tenantId);
}
