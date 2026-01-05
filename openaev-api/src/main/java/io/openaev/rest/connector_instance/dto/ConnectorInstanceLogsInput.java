package io.openaev.rest.connector_instance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnectorInstanceLogsInput {
  @Schema(description = "The connector instance logs")
  @JsonProperty("connector_instance_logs")
  private Set<String> logs;
}
