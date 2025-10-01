package io.openaev.database.specification;

import io.openaev.database.model.Comcheck;
import org.springframework.data.jpa.domain.Specification;

public class ComcheckSpecification {

  public static Specification<Comcheck> id(String dryRunId) {
    return (root, query, cb) -> cb.equal(root.get("id"), dryRunId);
  }

  public static Specification<Comcheck> fromExercise(String exerciseId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), exerciseId);
  }
}
