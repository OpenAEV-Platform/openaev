package io.openaev.database.repository;

import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import jakarta.validation.constraints.NotNull;
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

  /** Used by cleanup jobs to reap generations stuck in a transient status. */
  List<ReportingGeneration> findAllByStatus(@NotNull ReportingGenerationStatus status);

  /**
   * Among the given documents, the ones produced by a report generation, used by the generic
   * documents management surface to mark them read-only (their lifecycle belongs to the Reporting
   * module).
   *
   * <p>Native, kept as a plain SQL query for its {@code IN} shape. {@code reporting_generations} is
   * tenant-active, so the statement inspector scopes this query to the same {@code TxCtx} as the
   * enclosing {@code documents} transaction (both entrypoints carry the request's write/read
   * scope), the same tenant a generation and its produced document always share.
   *
   * <p>Confined to the documents being displayed rather than listing every generation output of the
   * platform, so the cost does not grow with the number of tenants.
   */
  @Query(
      value =
          "SELECT DISTINCT document_id FROM reporting_generations WHERE document_id IN"
              + " (:documentIds)",
      nativeQuery = true)
  List<String> documentIdsAmong(@Param("documentIds") @NotNull Collection<String> documentIds);

  /**
   * Whether a document is the output of a report generation (see {@link #documentIdsAmong}). Native
   * for the same reason: the guard must refuse on both routes.
   */
  @Query(
      value = "SELECT COUNT(*) FROM reporting_generations WHERE document_id = :documentId",
      nativeQuery = true)
  long countByDocumentId(@Param("documentId") @NotNull String documentId);
}
