package io.openaev.engine.model.securityplatform;

import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "security-platform", label = "Security Platform")
public class EsSecurityPlatform extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */
  @Queryable(label = "security platform name")
  private String name;
}
