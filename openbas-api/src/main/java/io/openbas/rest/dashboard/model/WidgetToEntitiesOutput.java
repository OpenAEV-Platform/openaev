package io.openbas.rest.dashboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbas.engine.api.ListConfiguration;
import io.openbas.engine.model.EsBase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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
  private List<EsBase> esEntities;
}
