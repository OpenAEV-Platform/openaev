package io.openaev.rest.settings.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalderaSettings {

  @JsonProperty("executor_caldera_instance_id")
  @Schema(description = "Id of the instance linked to the configuration")
  private String instanceId;

  @JsonProperty("executor_caldera_enable")
  @Schema(description = "True if the Caldera Executor is enabled")
  private Boolean executorCalderaEnable;

  @JsonProperty("executor_caldera_public_url")
  @Schema(description = "Url of the Caldera Executor")
  private String executorCalderaPublicUrl;
}
