package io.openaev.database.specification;

import io.openaev.database.model.Article;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.domain.Specification;

public class ArticleSpecification {

  private ArticleSpecification() {}

  public static Specification<Article> fromExercise(@NotBlank final String exerciseId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), exerciseId);
  }

  public static Specification<Article> fromScenario(@NotBlank final String scenarioId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), scenarioId);
  }
}
