package io.openaev.rest.asset.endpoint.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@SuperBuilder
@Schema(
    discriminatorProperty = "endpoint_has_full_details",
    oneOf = {EndpointTargetOutput.class, EndpointOutput.class},
    discriminatorMapping = {
      @DiscriminatorMapping(value = "false", schema = EndpointTargetOutput.class),
      @DiscriminatorMapping(value = "true", schema = EndpointOutput.class),
    })
public class EndpointBaseOutput {

  @Schema(description = "Asset Id")
  @JsonProperty("asset_id")
  @NotBlank
  private String id;
}
