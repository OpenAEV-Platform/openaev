package io.openaev.database.specification;

import io.openaev.database.model.Payload;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public class PayloadSpecification {

  private PayloadSpecification() {}

  /**
   * Fetches {@code collectorType} in the same query instead of leaving it lazy. {@code
   * CollectorTypeNameSerializer} calls {@code getName()} on that association during Jackson
   * serialization of the HTTP response, after the controller's transaction and tenant scope have
   * already closed; a lazy load at that point fails closed once {@code collector_types} is v2
   * tenant-active. The fetch runs inside the request's own scoped query instead, so the value is
   * already loaded by the time serialization happens.
   *
   * <p>Guarded against the count query Spring Data JPA builds for pagination: a fetch there would
   * either be rejected or silently ignored depending on the provider, so it is skipped for a {@code
   * Long} result type, same as the count query never needing the association at all.
   */
  public static Specification<Payload> withCollectorType() {
    return (root, query, cb) -> {
      if (query.getResultType() != Long.class && query.getResultType() != long.class) {
        root.fetch("collectorType", JoinType.LEFT);
      }
      return null;
    };
  }
}
