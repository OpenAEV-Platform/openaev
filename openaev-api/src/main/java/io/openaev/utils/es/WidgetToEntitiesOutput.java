package io.openaev.utils.es;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.query.EsEntities;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WidgetToEntitiesOutput {
  @Schema(
      description = "List configuration generated based on the input widget id and filter value")
  @JsonProperty("list_configuration")
  private ListConfiguration listConfiguration;

  @Schema(description = "List of entities")
  @JsonProperty("es_entities")
  private EsEntities esEntities;
}
