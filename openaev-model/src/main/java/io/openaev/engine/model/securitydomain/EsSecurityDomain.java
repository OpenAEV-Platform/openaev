package io.openaev.engine.model.securitydomain;

import io.openaev.annotation.EsQueryable;
import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "domain", label = "Domain")
public class EsSecurityDomain extends EsBase {

  @Queryable(label = "domain color", filterable = true)
  @EsQueryable(keyword = true)
  private String domain_color;

  // --SIDE --

  @Queryable(label = "tenant", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_tenant_side;
}
