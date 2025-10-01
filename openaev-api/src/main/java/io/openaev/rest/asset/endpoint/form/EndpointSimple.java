package io.openaev.rest.asset.endpoint.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(NON_NULL)
public class EndpointSimple {

  @Schema(description = "Asset Id")
  @JsonProperty("asset_id")
  @NotBlank
  private String id;

  @Schema(description = "Asset name")
  @JsonProperty("asset_name")
  @NotBlank
  private String name;
}
