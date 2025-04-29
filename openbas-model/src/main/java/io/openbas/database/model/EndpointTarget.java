package io.openbas.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbas.annotation.Queryable;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.NotImplementedException;

public class EndpointTarget extends InjectTarget {
  public EndpointTarget(String id, String name, Set<String> tags, String subType) {
    this.setId(id);
    this.setName(name);
    this.setTags(tags);
    this.setTargetType("ASSETS");
    this.subType = subType;
  }

  @JsonIgnore private final String subType;

  @JsonIgnore
  @JsonProperty("target_asset_groups")
  @Queryable(searchable = true, filterable = true, dynamicValues = true)
  public Set<String> getAssetGroups() {
    // note that it's not possible at the entity level to fetch a complete set of
    // containing asset groups since dynamic filters do not implement a relationship
    // between the Asset and AssetGroup entities.
    // This is currently only dealt with at the API Service level.
    throw new NotImplementedException(
        "NOT AVAILABLE; this property exists for exposing a filter only.");
  }

  @Override
  protected String getTargetSubtype() {
    return Optional.ofNullable(this.subType).orElse(Endpoint.PLATFORM_TYPE.Unknown.name());
  }
}
