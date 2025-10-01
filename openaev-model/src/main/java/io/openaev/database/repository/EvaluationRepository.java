package io.openaev.database.repository;

import io.openaev.database.model.Evaluation;
import io.openaev.database.raw.RawEvaluation;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationRepository
    extends CrudRepository<Evaluation, String>, JpaSpecificationExecutor<Evaluation> {

  @NotNull
  Optional<Evaluation> findById(@NotNull String id);

  @Query(
      value = "SELECT * FROM evaluations WHERE evaluation_objective IN :ids ;",
      nativeQuery = true)
  List<RawEvaluation> rawByObjectiveIds(@Param("ids") List<String> ids);
}
