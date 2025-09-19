package io.openbas.service.detection_remediation;

import io.openbas.api.detection_remediation.dto.PayloadInput;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.Command;
import io.openbas.database.model.DnsResolution;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetectionRemediationRequest {
  @Schema(
      description =
          "Concatenated payload string containing: Name, Type, optional Hostname/Command details, Description, Platform, Attack patterns, Architecture, Arguments")
  String payload;

  public DetectionRemediationRequest(
      PayloadInput payloadInput, List<AttackPattern> attackPatterns) {
    StringBuilder payload = new StringBuilder();
    payload.append("Name: ").append(payloadInput.getName()).append("\n");
    payload.append("Type: ").append(payloadInput.getType()).append("\n");

    if (payloadInput.getType().equals(DnsResolution.DNS_RESOLUTION_TYPE))
      payload.append("Hostname: ").append(payloadInput.getHostname()).append("\n");

    if (payloadInput.getType().equals(Command.COMMAND_TYPE)) {
      payload.append("Command executor: ").append(payloadInput.getExecutor()).append("\n");
      payload.append("Attack command: ").append(payloadInput.getContent()).append("\n");
    }

    if (payloadInput.getDescription() != null)
      payload.append("Description: ").append(payloadInput.getDescription()).append("\n");

    if (payloadInput.getPlatforms() != null && payloadInput.getPlatforms().length > 0)
      payload
          .append("Platform : ")
          .append(
              Arrays.stream(payloadInput.getPlatforms())
                  .map(Enum::name)
                  .collect(Collectors.joining(", ")))
          .append("\n");

    if (attackPatterns != null && !attackPatterns.isEmpty())
      payload
          .append("Attack patterns: ")
          .append(
              attackPatterns.stream()
                  .map(a -> "[" + a.getExternalId() + "]" + a.getName())
                  .collect(Collectors.joining(",\n ")))
          .append("\n");

    payload.append("Architecture: ").append(payloadInput.getExecutionArch()).append("\n");
    payload
        .append("Arguments: ")
        .append(
            payloadInput.getArguments().stream()
                .map(arg -> arg.getKey() + " : " + arg.getDefaultValue())
                .collect(Collectors.joining(", \n")))
        .append("\n");

    this.payload = payload.toString();
  }
}
