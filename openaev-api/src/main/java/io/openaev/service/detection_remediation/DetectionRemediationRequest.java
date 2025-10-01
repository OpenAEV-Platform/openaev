package io.openaev.service.detection_remediation;

import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.database.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class DetectionRemediationRequest {
  private final String TYPE_NOT_IMPLEMENTED_ERROR =
      "AI Webservice for FileDrop or Executable File not implemented";
  private final String[] TYPE_NOT_IMPLEMENTED = {
    FileDrop.FILE_DROP_TYPE, Executable.EXECUTABLE_TYPE
  };

  @Schema(
      description =
          "Concatenated payload string containing: Name, Type, optional Hostname/Command details, Description, Platform, Attack patterns, Architecture, Arguments")
  private String payload;

  public DetectionRemediationRequest(
      PayloadInput payloadInput, List<AttackPattern> attackPatterns) {
    if (payloadInput.getType().equals(Executable.EXECUTABLE_TYPE)
        || payloadInput.getType().equals(FileDrop.FILE_DROP_TYPE)) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, TYPE_NOT_IMPLEMENTED_ERROR);
    }

    setPayload(
        payloadInput.getName(),
        payloadInput.getType(),
        payloadInput.getExecutor(),
        payloadInput.getContent(),
        payloadInput.getHostname(),
        payloadInput.getDescription(),
        payloadInput.getPlatforms(),
        attackPatterns,
        payloadInput.getExecutionArch(),
        payloadInput.getArguments());
  }

  public DetectionRemediationRequest(Payload payloadInput) {
    if (Arrays.stream(TYPE_NOT_IMPLEMENTED).anyMatch(type -> type.equals(payloadInput.getType()))) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, TYPE_NOT_IMPLEMENTED_ERROR);
    }
    String commandExecutor = null;
    String attackCommand = null;

    if (payloadInput instanceof Command) {
      commandExecutor = ((Command) payloadInput).getExecutor();
      attackCommand = ((Command) payloadInput).getContent();
    }

    String hostname = null;
    if (payloadInput instanceof DnsResolution)
      hostname = ((DnsResolution) payloadInput).getHostname();

    setPayload(
        payloadInput.getName(),
        payloadInput.getType(),
        commandExecutor,
        attackCommand,
        hostname,
        payloadInput.getDescription(),
        payloadInput.getPlatforms(),
        payloadInput.getAttackPatterns(),
        payloadInput.getExecutionArch(),
        payloadInput.getArguments());
  }

  private void setPayload(
      @NotNull String name,
      @NotNull String type,
      String commandExecutor,
      String attackCommand,
      String hostname,
      String description,
      Endpoint.PLATFORM_TYPE[] plateform,
      List<AttackPattern> attackPatterns,
      @NotNull Payload.PAYLOAD_EXECUTION_ARCH executionArch,
      List<PayloadArgument> arguments) {

    StringBuilder payloadDetectionRemediation = new StringBuilder();
    payloadDetectionRemediation.append("Name: ").append(name).append("\n");
    payloadDetectionRemediation.append("Type: ").append(type).append("\n");

    if (type.equals(Command.COMMAND_TYPE)) {
      if (commandExecutor != null)
        payloadDetectionRemediation
            .append("Command executor: ")
            .append(commandExecutor)
            .append("\n");

      if (attackCommand != null)
        payloadDetectionRemediation.append("Attack command: ").append(attackCommand).append("\n");
    }

    if (hostname != null && type.equals(DnsResolution.DNS_RESOLUTION_TYPE))
      payloadDetectionRemediation.append("Hostname: ").append(hostname).append("\n");

    if (description != null)
      payloadDetectionRemediation.append("Description: ").append(description).append("\n");

    if (plateform != null && plateform.length > 0)
      payloadDetectionRemediation
          .append("Platform : ")
          .append(Arrays.stream(plateform).map(Enum::name).collect(Collectors.joining(", ")))
          .append("\n");

    if (attackPatterns != null && !attackPatterns.isEmpty())
      payloadDetectionRemediation
          .append("Attack patterns: ")
          .append(
              attackPatterns.stream()
                  .map(a -> "[" + a.getExternalId() + "]" + a.getName())
                  .collect(Collectors.joining(",\n ")))
          .append("\n");

    payloadDetectionRemediation.append("Architecture: ").append(executionArch).append("\n");

    if (arguments != null && !arguments.isEmpty())
      payloadDetectionRemediation
          .append("Arguments: ")
          .append(
              arguments.stream()
                  .map(arg -> arg.getKey() + " : " + arg.getDefaultValue())
                  .collect(Collectors.joining(", \n")))
          .append("\n");

    this.payload = payloadDetectionRemediation.toString();
  }
}
