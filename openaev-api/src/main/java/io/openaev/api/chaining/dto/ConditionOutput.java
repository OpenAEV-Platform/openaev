package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.PrimitiveType;
import java.util.List;
import lombok.*;

/** Nested output DTO for a single condition inside an event. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConditionOutput {
  @JsonProperty("condition_id")
  private String id;

  @JsonProperty("condition_key")
  private String key;

  @JsonProperty("condition_key_types")
  private List<PrimitiveType> keyTypes;

  @JsonProperty("condition_type")
  private String type;

  @JsonProperty("condition_value")
  private String value;

  @JsonProperty("condition_case_sensitive")
  private boolean caseSensitive;

  @JsonProperty("condition_parent_id")
  private String conditionParentId;

  @JsonProperty("condition_mapping_type")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private MappingType mappingType;
}
