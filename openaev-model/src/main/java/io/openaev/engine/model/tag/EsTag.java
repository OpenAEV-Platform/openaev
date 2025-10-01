package io.openaev.engine.model.tag;

import io.openaev.annotation.EsQueryable;
import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "tag", label = "Tag")
public class EsTag extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "tag color", filterable = true)
  @EsQueryable(keyword = true)
  private String tag_color;
}
