package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import java.util.Set;
import lombok.Data;

@Data
public class AssetGroupTarget extends InjectTarget {
  public AssetGroupTarget(String id, String name, Set<String> tags) {
    this.setId(id);
    this.setName(name);
    this.setTags(tags);
    this.setTargetType("ASSETS_GROUPS");
  }

  @JsonProperty("target_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  private String name;

  @Override
  protected String getTargetSubtype() {
    return "N/A";
  }
}
