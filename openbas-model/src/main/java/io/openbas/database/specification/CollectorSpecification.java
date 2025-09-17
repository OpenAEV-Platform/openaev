package io.openbas.database.specification;

import io.openbas.database.model.Collector;
import org.springframework.data.jpa.domain.Specification;

public class CollectorSpecification {

  private CollectorSpecification() {}

  public static Specification<Collector> hasSecurityPlatform() {
    return (root, query, cb) -> cb.isNotNull(root.get("securityPlatform"));
  }
}
