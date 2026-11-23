package io.openaev.database.specification;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Injector;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public class CatalogConnectorInjectorContractSpecification {

  private CatalogConnectorInjectorContractSpecification() {}

  /** The contracts of the catalogue injectors the tenant does not run, with their injector. */
  public static Specification<CatalogConnectorInjectorContract> ofInjectorsNotRegisteredIn(
      String tenantId) {
    return (root, query, cb) -> {
      if (query.getResultType() != Long.class && query.getResultType() != long.class) {
        root.fetch("catalogConnector");
      }
      Path<CatalogConnector> connector = root.get("catalogConnector");
      Subquery<Integer> registered = query.subquery(Integer.class);
      Root<Injector> injector = registered.from(Injector.class);
      registered
          .select(cb.literal(1))
          .where(
              cb.equal(injector.get("type"), connector.get("slug")),
              cb.equal(injector.get("tenantId"), tenantId));
      return cb.and(
          cb.equal(connector.get("containerType"), ConnectorType.INJECTOR),
          cb.isNull(connector.get("deletedAt")),
          cb.not(cb.exists(registered)));
    };
  }
}
