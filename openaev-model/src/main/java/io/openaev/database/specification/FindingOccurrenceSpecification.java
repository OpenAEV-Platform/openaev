package io.openaev.database.specification;

import io.openaev.database.model.FindingOccurrence;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class FindingOccurrenceSpecification {

  private FindingOccurrenceSpecification() {}

  // Joined rather than read from the foreign key: finding_occurrences has no tenant of its own, the
  // join on the finding is what scopes the read
  public static Specification<FindingOccurrence> ofFinding(@NotNull final String findingId) {
    return (root, query, cb) -> cb.equal(root.join("finding").get("id"), findingId);
  }
}
