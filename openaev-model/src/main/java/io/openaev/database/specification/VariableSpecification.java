package io.openaev.database.specification;

import io.openaev.database.model.Variable;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class VariableSpecification {

  public static Specification<Variable> fromExercise(@NotNull final String exerciseId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), exerciseId);
  }

  public static Specification<Variable> fromScenario(@NotNull final String scenarioId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), scenarioId);
  }
}
