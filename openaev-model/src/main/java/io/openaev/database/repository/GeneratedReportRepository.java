package io.openaev.database.repository;

import io.openaev.database.model.GeneratedReport;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeneratedReportRepository
    extends CrudRepository<GeneratedReport, String>, JpaSpecificationExecutor<GeneratedReport> {

  List<GeneratedReport> findAllByExerciseIdOrderByCreationDateDesc(
      @NotBlank final String exerciseId);

  Optional<GeneratedReport> findByIdAndExerciseId(
      @NotBlank final String id, @NotBlank final String exerciseId);

  /**
   * Global reports (covering every simulation) are tracked with both {@code exercise} and {@code
   * scenario} left {@code null} - Scenario reports also leave {@code exercise} null, so this must
   * additionally exclude rows that have a scenario set.
   */
  List<GeneratedReport> findAllByExerciseIsNullAndScenarioIsNullOrderByCreationDateDesc();

  Optional<GeneratedReport> findByIdAndExerciseIsNull(@NotBlank final String id);

  List<GeneratedReport> findAllByScenarioIdOrderByCreationDateDesc(
      @NotBlank final String scenarioId);

  Optional<GeneratedReport> findByIdAndScenarioId(
      @NotBlank final String id, @NotBlank final String scenarioId);

  /**
   * Every report regardless of scope (global/simulation/scenario), for the unified "Reports" page.
   */
  List<GeneratedReport> findAllByOrderByCreationDateDesc();
}
