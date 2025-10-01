package io.openaev.database.specification;

import io.openaev.database.model.Challenge;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class ChallengeSpecification {
  private ChallengeSpecification() {}

  public static Specification<Challenge> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }
}
