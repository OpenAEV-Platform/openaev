package io.openaev.engine.model.tenant;

import io.openaev.annotation.EsQueryable;
import io.openaev.annotation.Queryable;
import io.openaev.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EsTenantBase extends EsBase {

  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "tenant", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_tenant_side; // Must finish by _side
}
