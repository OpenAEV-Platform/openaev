package io.openaev.database.specification;

import io.openaev.database.model.Challenge;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class ChallengeSpecification {
  private ChallengeSpecification() {}

  public static Specification<Challenge> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }
}
