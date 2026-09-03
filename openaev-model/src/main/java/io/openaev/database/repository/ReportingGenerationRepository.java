package io.openaev.database.repository;

import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportingGenerationRepository
    extends CrudRepository<ReportingGeneration, String>,
        JpaSpecificationExecutor<ReportingGeneration> {

  Optional<ReportingGeneration> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  /**
   * Reloads a generation with its produced document initialized, for use outside an open session
   * (e.g. the scheduling engine polling for a terminal status before emailing the file).
   */
  @Query(
      "select g from ReportingGeneration g "
          + "left join fetch g.document "
          + "where g.id = :id and g.tenant.id = :tenantId")
  Optional<ReportingGeneration> findWithDocumentByIdAndTenantId(
      @Param("id") @NotNull String id, @Param("tenantId") @NotNull String tenantId);

  List<ReportingGeneration> findAllByReportingIdOrderByCreatedAtDesc(@NotNull String reportingId);

  /**
   * Generations left in a transient status (PENDING, RUNNING) since before the given instant, all
   * tenants together: a render whose thread died, hung or was lost to a restart never writes a
   * terminal status by itself. Used by the reaper job.
   */
  List<ReportingGeneration> findAllByStatusInAndCreatedAtBefore(
      @NotNull Collection<ReportingGenerationStatus> statuses, @NotNull Instant createdAt);

  /**
   * Ids of the documents produced by report generations, used by the generic documents management
   * surface to mark those documents read-only (their lifecycle belongs to the Reporting module).
   */
  @Query("select distinct g.document.id from ReportingGeneration g where g.document is not null")
  List<String> documentIds();

  /** Whether a document is the output of a report generation (see {@link #documentIds()}). */
  boolean existsByDocumentId(@NotNull String documentId);
}
