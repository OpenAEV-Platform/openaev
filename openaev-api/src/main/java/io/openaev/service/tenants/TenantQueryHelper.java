package io.openaev.service.tenants;

import static io.openaev.api.tenants.TenantOutput.*;

import io.openaev.api.tenants.TenantOutput;
import io.openaev.database.model.Tenant;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;

public class TenantQueryHelper {

  private TenantQueryHelper() {}

  // -- SELECT --
  public static void select(CriteriaQuery<Tuple> cq, Root<Tenant> root) {
    cq.multiselect(
            root.get("id").alias(ALIAS_ID),
            root.get("name").alias(ALIAS_NAME),
            root.get("description").alias(ALIAS_DESCRIPTION))
        .distinct(true);
  }

  // -- EXECUTION --
  public static List<TenantOutput> execution(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                new TenantOutput(
                    tuple.get(ALIAS_ID, String.class),
                    tuple.get(ALIAS_NAME, String.class),
                    tuple.get(ALIAS_DESCRIPTION, String.class)))
        .toList();
  }
}
