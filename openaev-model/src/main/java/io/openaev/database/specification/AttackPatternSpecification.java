package io.openaev.database.specification;

import io.openaev.database.model.AttackPattern;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;

public class AttackPatternSpecification {

  public static Specification<AttackPattern> fromAttackPattern(String attackPatternId) {
    return (root, query, cb) -> cb.equal(root.get("attackPatterns").get("id"), attackPatternId);
  }

  public static Specification<AttackPattern> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
