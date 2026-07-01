package io.openaev.database.repository;

import io.openaev.database.model.Report;
import io.openaev.database.model.ReportInjectComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository
    extends CrudRepository<Report, UUID>, JpaSpecificationExecutor<Report> {
  @NotNull
  Optional<Report> findById(@NotNull final UUID id);

  @Query("SELECT r FROM Report r JOIN r.exercise e WHERE r.id = :id AND e.tenant.id = :tenantId")
  Optional<Report> findByIdAndTenantId(
      @NotNull @Param("id") UUID id, @NotBlank @Param("tenantId") String tenantId);

  @Query(
      value =
          "SELECT injectComment FROM ReportInjectComment injectComment WHERE injectComment.report.id = :reportId AND injectComment.inject.id = :injectId")
  Optional<ReportInjectComment> findReportInjectComment(
      @NotNull final UUID reportId, @NotNull final String injectId);

  @Query(
      "SELECT r FROM Report r JOIN r.exercise e WHERE r.id = :id AND e.id = :exerciseId AND e.tenant.id = :tenantId")
  Optional<Report> findByIdAndExercise_IdAndTenantId(
      @NotNull @Param("id") UUID id,
      @NotBlank @Param("exerciseId") String exerciseId,
      @NotBlank @Param("tenantId") String tenantId);
}
