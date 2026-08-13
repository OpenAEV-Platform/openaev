package io.openaev.rest.collector.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.connector.dto.ConnectorOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Schema(description = "Collector output")
public class CollectorOutput extends ConnectorOutput {

  @Schema(description = "Collector id")
  @JsonProperty("collector_id")
  @NotBlank
  private String id;

  @JsonProperty("collector_name")
  @NotBlank
  private String name;

  @JsonProperty("collector_type")
  @NotBlank
  private String type;
}
