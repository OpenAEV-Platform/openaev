package io.openaev.database.specification;

import io.openaev.database.model.Report;
import org.springframework.data.jpa.domain.Specification;

public class ReportSpecification {

  public static Specification<Report> fromExercise(String exerciseId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), exerciseId);
  }

  public static Specification<Report> fromExerciseAndTenant(String exerciseId, String tenantId) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("exercise").get("id"), exerciseId),
            cb.equal(root.get("exercise").get("tenant").get("id"), tenantId));
  }
}
