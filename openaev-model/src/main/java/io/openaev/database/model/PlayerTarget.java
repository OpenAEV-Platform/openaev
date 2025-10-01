package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import java.util.Set;
import lombok.Data;

@Data
public class PlayerTarget extends InjectTarget {
  public PlayerTarget(String id, String name, Set<String> tags, Set<String> teams) {
    this.setId(id);
    this.setName(name);
    this.setTags(tags);
    this.setTeams(teams);
    this.setTargetType("PLAYERS");
  }

  @JsonProperty("target_name")
  private String name;

  @Override
  protected String getTargetSubtype() {
    return this.getTargetType();
  }

  @JsonProperty("target_teams")
  @Queryable(filterable = true, searchable = true, sortable = true, dynamicValues = true)
  private Set<String> teams;
}
