package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.rest.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.rest.threat_arsenal.dto.ThreatArsenalActionFullOutput;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ThreatArsenalMapper {

  private final PayloadMapper payloadMapper;
  private final InjectorContractContentUtils injectorContractContentUtils;

  /**
   * Convert an injectorContract to a ThreatArsenalAction
   *
   * @param injectorContract the injectorContract to convert
   * @return the threat arsenal action DTO
   */
  public ThreatArsenalAction toThreatArsenalAction(InjectorContract injectorContract) {
    if (injectorContract == null) {
      return null;
    }
    return ThreatArsenalAction.builder()
        .id(injectorContract.getId())
        .labels(injectorContract.getLabels())
        .injectorType(injectorContract.getInjectorType())
        .domains(
            injectorContract.getDomains().stream().map(d -> d.getId()).collect(Collectors.toSet()))
        .platforms(injectorContract.getPlatforms())
        .tags(injectorContract.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .updatedAt(injectorContract.getUpdatedAt())
        .payload(payloadMapper.toPayloadSimple(Optional.ofNullable(injectorContract.getPayload())))
        .build();
  }

  public ThreatArsenalActionFullOutput toThreatArsenalActionFullOutput(
      InjectorContract injectorContract) {
    return new ThreatArsenalActionFullOutput(
        injectorContract.getId(),
        injectorContract.getInjectorType(),
        injectorContract.getLabels(),
        null,
        injectorContract.getPlatforms(),
        null,
        null,
        null,
        null,
        injectorContract.getExternalId(),
        null,
        injectorContractContentUtils.getPredefinedExpectations(injectorContract),
        null,
        null,
        null,
        null,
        null,
        injectorContract.getTags().stream().map(Tag::getId).toList(),
        injectorContract.getDomains().stream().map(Domain::getId).toList(),
        injectorContract.getAttackPatterns().stream().map(AttackPattern::getId).toList(),
        null,
        null,
        null,
        null,
        null,
        injectorContract.getCreatedAt(),
        injectorContract.getUpdatedAt());
  }

  /**
   * Converts a {@link Payload} entity and its related IDs into a full-detail {@link
   * ThreatArsenalActionFullOutput} record.
   *
   * @param payload the payload entity to convert
   * @param attackPatternIds attack-pattern IDs linked through the injector contract
   * @param domainIds domain IDs linked through the injector contract
   * @param tagIds tag IDs linked through the injector contract
   * @return the fully populated action output DTO
   */
  public ThreatArsenalActionFullOutput toThreatArsenalActionFullOutput(
      @NotNull Payload payload,
      @NotNull String injectorContractId,
      @NotNull Map<String, String> labels,
      List<String> attackPatternIds,
      List<String> domainIds,
      List<String> tagIds) {

    String commandExecutor = null;
    String commandContent = null;
    String dnsResolutionHostname = null;
    String fileDropFile = null;
    String executableFile = null;

    Payload unproxied = (Payload) Hibernate.unproxy(payload);
    if (unproxied instanceof Command command) {
      commandExecutor = command.getExecutor();
      commandContent = command.getContent();
    } else if (unproxied instanceof DnsResolution dnsResolution) {
      dnsResolutionHostname = dnsResolution.getHostname();
    } else if (unproxied instanceof FileDrop fileDrop && fileDrop.getFileDropFile() != null) {
      fileDropFile = fileDrop.getFileDropFile().getId();
    } else if (unproxied instanceof Executable executable
        && executable.getExecutableFile() != null) {
      executableFile = executable.getExecutableFile().getId();
    }

    return new ThreatArsenalActionFullOutput(
        injectorContractId,
        payload.getType(),
        labels,
        payload.getDescription(),
        payload.getPlatforms(),
        payload.getCleanupExecutor(),
        payload.getCleanupCommand(),
        payload.getArguments(),
        payload.getPrerequisites(),
        payload.getExternalId(),
        payload.getSource(),
        payload.getExpectations(),
        payload.getStatus(),
        payload.getExecutionArch(),
        payload.getCollectorTypeValue(),
        payload.getDetectionRemediations(),
        payload.getOutputParsers(),
        tagIds,
        domainIds,
        attackPatternIds,
        commandExecutor,
        commandContent,
        dnsResolutionHostname,
        fileDropFile,
        executableFile,
        payload.getCreatedAt(),
        payload.getUpdatedAt());
  }
}
