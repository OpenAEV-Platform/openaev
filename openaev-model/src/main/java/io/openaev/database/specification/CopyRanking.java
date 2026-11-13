package io.openaev.database.specification;

import io.openaev.database.model.Tenant;
import java.util.Comparator;

/**
 * The ranking of the copies of one id held by several tenants of a scope: the default tenant's copy
 * when the scope holds it, else the copy of the lowest tenant id. {@link
 * InjectorContractSpecification#onePerContractId} states the same rule in SQL; code that reads the
 * copies into memory sorts them with {@link #BY_TENANT_ID} and keeps the first of each id.
 */
public final class CopyRanking {

  private CopyRanking() {}

  /** Orders tenant ids best-ranked first. */
  public static final Comparator<String> BY_TENANT_ID =
      Comparator.comparing((String tenantId) -> !Tenant.DEFAULT_TENANT_UUID.equals(tenantId))
          .thenComparing(Comparator.naturalOrder());
}
