package io.openaev.api.catalog.dto.action;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/** An action of the catalogue the tenant does not have yet, by what brings it. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = AvailableActionOutput.KIND)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = AvailablePayloadActionOutput.class,
      name = AvailableActionOutput.PAYLOAD),
  @JsonSubTypes.Type(
      value = AvailableIntegrationActionOutput.class,
      name = AvailableActionOutput.INTEGRATION)
})
@Schema(
    oneOf = {AvailablePayloadActionOutput.class, AvailableIntegrationActionOutput.class},
    discriminatorProperty = AvailableActionOutput.KIND,
    discriminatorMapping = {
      @DiscriminatorMapping(
          value = AvailableActionOutput.PAYLOAD,
          schema = AvailablePayloadActionOutput.class),
      @DiscriminatorMapping(
          value = AvailableActionOutput.INTEGRATION,
          schema = AvailableIntegrationActionOutput.class)
    })
public sealed interface AvailableActionOutput
    permits AvailablePayloadActionOutput, AvailableIntegrationActionOutput {

  String KIND = "action_kind";
  String PAYLOAD = "PAYLOAD";
  String INTEGRATION = "INTEGRATION";
}
