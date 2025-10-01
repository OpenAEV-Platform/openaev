package io.openaev.engine.model.assetgroup;

import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "asset-group", label = "Asset group")
public class EsAssetGroup extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */
  @Queryable(label = "asset group name")
  private String name;
}
