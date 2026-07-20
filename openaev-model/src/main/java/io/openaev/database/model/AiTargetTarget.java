package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import java.util.Optional;
import java.util.Set;
import lombok.Data;

/**
 * Inject target backed by an AI target {@link Asset} ({@code category = AI_TARGET} - an LLM
 * endpoint / AI agent under adversarial test). Unlike endpoint / agent targets, an AI target is
 * referenced from the inject content (the {@code ai_target} field) rather than through an asset
 * relation, so it is resolved by the {@code AiTargetSearchAdaptor} from the inject content.
 */
@Data
public class AiTargetTarget extends InjectTarget {

  public AiTargetTarget(String id, String name, Set<String> tags, String subType) {
    this.setId(id);
    this.setName(name);
    this.setTags(tags);
    this.setTargetType("AI_TARGETS");
    this.subType = subType;
  }

  @JsonProperty("target_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  private String name;

  @JsonIgnore private final String subType;

  @Override
  protected String getTargetSubtype() {
    return Optional.ofNullable(this.subType).orElse("Unknown");
  }
}
