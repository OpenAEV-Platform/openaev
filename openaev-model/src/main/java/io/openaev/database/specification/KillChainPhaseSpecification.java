package io.openaev.database.specification;

import io.openaev.database.model.KillChainPhase;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;

public class KillChainPhaseSpecification {

  private KillChainPhaseSpecification() {}

  public static Specification<KillChainPhase> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }

  /**
   * Matches the search text against the phase name or the kill chain name, so that multi kill chain
   * option pickers (labelled "[kill chain] phase") can be narrowed by either part.
   */
  public static Specification<KillChainPhase> byNameOrKillChainName(
      @Nullable final String searchText) {
    return (root, query, cb) -> {
      if (searchText == null || searchText.isEmpty()) {
        return cb.conjunction();
      }
      String likePattern = "%" + searchText.toLowerCase() + "%";
      return cb.or(
          cb.like(cb.lower(root.get("name")), likePattern),
          cb.like(cb.lower(root.get("killChainName")), likePattern));
    };
  }
}
