package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.PrimitiveType;
import java.util.List;
import lombok.*;

/** Output DTO for mapper conditions attached to a step. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MapperConditionOutput {

  @JsonProperty("condition_key_types")
  private List<PrimitiveType> conditionKeyTypes;

  @JsonProperty("condition_key")
  private String conditionKey;

  @JsonProperty("condition_value")
  private String conditionValue;

  @JsonProperty("condition_mapping_type")
  private MappingType conditionMappingType;
}
