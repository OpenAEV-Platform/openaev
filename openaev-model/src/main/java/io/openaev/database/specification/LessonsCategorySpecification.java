package io.openaev.database.specification;

import io.openaev.database.model.LessonsCategory;
import org.springframework.data.jpa.domain.Specification;

public class LessonsCategorySpecification {

  public static Specification<LessonsCategory> fromExercise(String exerciseId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), exerciseId);
  }

  public static Specification<LessonsCategory> fromScenario(String scenarioId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), scenarioId);
  }
}
