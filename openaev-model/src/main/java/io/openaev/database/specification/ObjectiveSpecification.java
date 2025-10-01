package io.openaev.database.specification;

import io.openaev.database.model.Objective;
import org.springframework.data.jpa.domain.Specification;

public class ObjectiveSpecification {

  public static Specification<Objective> fromExercise(String exerciseId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), exerciseId);
  }

  public static Specification<Objective> fromScenario(String scenarioId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), scenarioId);
  }
}
