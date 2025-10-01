package io.openaev.rest.report.repository;

import io.openaev.rest.report.model.Report;
import io.openaev.rest.report.model.ReportInjectComment;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository
    extends CrudRepository<Report, UUID>, JpaSpecificationExecutor<Report> {
  @NotNull
  Optional<Report> findById(@NotNull final UUID id);

  @Query(
      value =
          "SELECT injectComment FROM ReportInjectComment injectComment WHERE injectComment.report.id = :reportId AND injectComment.inject.id = :injectId")
  Optional<ReportInjectComment> findReportInjectComment(
      @NotNull final UUID reportId, @NotNull final String injectId);

  Optional<Report> findByIdAndExercise_Id(@NotNull UUID reportId, @NotBlank String exerciseId);
}
