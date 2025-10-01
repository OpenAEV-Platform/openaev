package io.openaev.database.specification;

import io.openaev.database.model.Document;
import jakarta.persistence.criteria.Path;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.domain.Specification;

public class DocumentSpecification {

  private DocumentSpecification() {}

  public static Specification<Document> findGrantedFor(@NotBlank final String userId) {
    return (root, query, criteriaBuilder) -> {
      Path<Object> exercisePath =
          root.join("exercises").join("grants").join("group").join("users").get("id");

      Path<Object> scenarioPath =
          root.join("scenarios").join("grants").join("group").join("users").get("id");

      return criteriaBuilder.or(
          criteriaBuilder.equal(exercisePath, userId), criteriaBuilder.equal(scenarioPath, userId));
    };
  }
}
