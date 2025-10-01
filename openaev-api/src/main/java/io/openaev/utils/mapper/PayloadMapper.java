package io.openaev.utils.mapper;

import static io.openaev.database.model.Command.COMMAND_TYPE;
import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.Executable.EXECUTABLE_TYPE;
import static io.openaev.database.model.FileDrop.FILE_DROP_TYPE;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.rest.atomic_testing.form.AttackPatternSimple;
import io.openaev.rest.atomic_testing.form.StatusPayloadOutput;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementSimple;
import io.openaev.rest.payload.form.DetectionRemediationOutput;
import io.openaev.rest.payload.output_parser.OutputParserSimple;
import io.openaev.rest.payload.regex_group.RegexGroupSimple;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class PayloadMapper {

  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ApplicationContext context;

  public StatusPayloadOutput getStatusPayloadOutputFromInject(Optional<Inject> inject) {

    if (inject.isEmpty()) {
      return null;
    }

    Inject injectObj = inject.get();
    Optional<InjectorContract> injectorContractOpt = injectObj.getInjectorContract();
    if (injectorContractOpt.isEmpty() || injectorContractOpt.get().getPayload() == null) {
      return null;
    }

    InjectorContract injectorContract = injectorContractOpt.get();
    StatusPayloadOutput.StatusPayloadOutputBuilder statusPayloadOutputBuilder =
        StatusPayloadOutput.builder();

    if (ofNullable(inject.get().getContent()).map(c -> c.has("obfuscator")).orElse(Boolean.FALSE)) {
      String obfuscator = inject.get().getContent().findValue("obfuscator").asText();
      statusPayloadOutputBuilder.obfuscator(obfuscator);
    }

    Optional<InjectStatus> injectStatusOpt = injectObj.getStatus();
    Payload payload = injectorContract.getPayload();

    // Handle the case when inject has not been executed yet or no payload output exists
    if (injectStatusOpt.isEmpty() || injectStatusOpt.get().getPayloadOutput() == null) {
      if (payload != null) {
        populatePayloadDetails(statusPayloadOutputBuilder, payload, injectorContract);

        // Handle different payload types
        processPayloadType(statusPayloadOutputBuilder, payload);
        return statusPayloadOutputBuilder.build();
      } else {
        return null;
      }
    }

    // If inject has been executed, reuse the previous status
    return injectStatusOpt
        .map(InjectStatus::getPayloadOutput)
        .map(
            statusPayload ->
                populateExecutedPayload(
                    statusPayloadOutputBuilder, statusPayload, injectorContract))
        .orElse(null);
  }

  private Set<RegexGroupSimple> toRegexGroupSimple(Set<RegexGroup> regexGroups) {
    return regexGroups.stream()
        .map(
            regexGroup ->
                RegexGroupSimple.builder()
                    .id(regexGroup.getId())
                    .field(regexGroup.getField())
                    .indexValues(regexGroup.getIndexValues())
                    .build())
        .collect(Collectors.toSet());
  }

  private Set<ContractOutputElementSimple> toContractOutputElementsSimple(
      Set<ContractOutputElement> contractElements) {
    return contractElements.stream()
        .map(
            contractElement ->
                ContractOutputElementSimple.builder()
                    .id(contractElement.getId())
                    .rule(contractElement.getRule())
                    .name(contractElement.getName())
                    .key(contractElement.getKey())
                    .type(contractElement.getType())
                    .tagIds(contractElement.getTags().stream().map(Tag::getId).toList())
                    .regexGroups(toRegexGroupSimple(contractElement.getRegexGroups()))
                    .build())
        .collect(Collectors.toSet());
  }

  private Set<OutputParserSimple> toOutputParsersSimple(Set<OutputParser> outputParsers) {
    return outputParsers.stream()
        .map(
            parser ->
                OutputParserSimple.builder()
                    .id(parser.getId())
                    .mode(parser.getMode())
                    .type(parser.getType())
                    .contractOutputElement(
                        toContractOutputElementsSimple(parser.getContractOutputElements()))
                    .build())
        .collect(Collectors.toSet());
  }

  private void populatePayloadDetails(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder,
      Payload payload,
      InjectorContract injectorContract) {
    builder
        .arguments(payload.getArguments())
        .prerequisites(payload.getPrerequisites())
        .externalId(payload.getExternalId())
        .cleanupExecutor(payload.getCleanupExecutor())
        .name(payload.getName())
        .type(payload.getType())
        .collectorType(payload.getCollectorType())
        .description(payload.getDescription())
        .tags(payload.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .platforms(payload.getPlatforms())
        .payloadOutputParsers(toOutputParsersSimple(payload.getOutputParsers()))
        .attackPatterns(toAttackPatternSimples(injectorContract.getAttackPatterns()))
        .executableArch(injectorContract.getArch());
  }

  private void processPayloadType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, Payload payload) {
    switch (payload.getType()) {
      case COMMAND_TYPE:
        handleCommandType(builder, (Command) Hibernate.unproxy(payload));
        break;
      case EXECUTABLE_TYPE:
        handleExecutableType(builder, (Executable) Hibernate.unproxy(payload));
        break;
      case FILE_DROP_TYPE:
        handleFileDropType(builder, (FileDrop) Hibernate.unproxy(payload));
        break;
      case DNS_RESOLUTION_TYPE:
        handleDnsResolutionType(builder, (DnsResolution) Hibernate.unproxy(payload));
        break;
      default:
        break;
    }
  }

  private void handleCommandType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, Command payloadCommand) {
    List<String> cleanupCommands = new ArrayList<>();
    if (payloadCommand.getCleanupCommand() != null) {
      cleanupCommands.add(payloadCommand.getCleanupCommand());
    }

    PayloadCommandBlock commandBlock =
        new PayloadCommandBlock(
            payloadCommand.getExecutor(), payloadCommand.getContent(), cleanupCommands);
    builder.payloadCommandBlocks(Collections.singletonList(commandBlock));
  }

  private void handleExecutableType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, Executable payloadExecutable) {
    builder.executableFile(new StatusPayloadDocument(payloadExecutable.getExecutableFile()));
  }

  private void handleFileDropType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, FileDrop payloadFileDrop) {
    builder.fileDropFile(new StatusPayloadDocument(payloadFileDrop.getFileDropFile()));
  }

  private void handleDnsResolutionType(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder, DnsResolution payloadDnsResolution) {
    builder.hostname(payloadDnsResolution.getHostname());
  }

  private StatusPayloadOutput populateExecutedPayload(
      StatusPayloadOutput.StatusPayloadOutputBuilder builder,
      StatusPayload statusPayload,
      InjectorContract injectorContract) {
    builder
        .cleanupExecutor(statusPayload.getCleanupExecutor())
        .payloadCommandBlocks(statusPayload.getPayloadCommandBlocks())
        .arguments(statusPayload.getArguments())
        .prerequisites(statusPayload.getPrerequisites())
        .externalId(statusPayload.getExternalId())
        .executableFile(statusPayload.getExecutableFile())
        .fileDropFile(statusPayload.getFileDropFile())
        .hostname(statusPayload.getHostname())
        .attackPatterns(toAttackPatternSimples(injectorContract.getAttackPatterns()))
        .executableArch(injectorContract.getArch())
        .name(statusPayload.getName())
        .type(statusPayload.getType())
        .description(statusPayload.getDescription())
        .platforms(injectorContract.getPlatforms());

    Payload payload = injectorContract.getPayload();
    if (payload != null) {
      builder
          .collectorType(payload.getCollectorType())
          .payloadOutputParsers(toOutputParsersSimple(payload.getOutputParsers()))
          .tags(payload.getTags().stream().map(Tag::getId).collect(Collectors.toSet()));
    }

    return builder.build();
  }

  public List<AttackPatternSimple> toAttackPatternSimples(List<AttackPattern> attackPatterns) {
    return attackPatterns.stream()
        .filter(Objects::nonNull)
        .map(this::toAttackPatternSimple)
        .toList();
  }

  private AttackPatternSimple toAttackPatternSimple(AttackPattern attackPattern) {
    return AttackPatternSimple.builder()
        .id(attackPattern.getId())
        .name(attackPattern.getName())
        .externalId(attackPattern.getExternalId())
        .build();
  }

  public List<DetectionRemediationOutput> toDetectionRemediationOutputs(
      List<DetectionRemediation> detectionRemediations) {
    if (eeService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      return detectionRemediations.stream()
          .map(PayloadMapper::toDetectionRemediationOutput)
          .toList();
    } else {
      log.debug("Enterprise Edition license inactive - omitting remediation information");
      return emptyList();
    }
  }

  public static DetectionRemediationOutput toDetectionRemediationOutput(
      DetectionRemediation detectionRemediation) {
    return DetectionRemediationOutput.builder()
        .id(detectionRemediation.getId())
        .payloadId(detectionRemediation.getPayload().getId())
        .collectorType(detectionRemediation.getCollector().getType())
        .values(detectionRemediation.getValues())
        .authorRule(detectionRemediation.getAuthorRule())
        .build();
  }

  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(Set<Payload> payloads) {
    return payloads.stream().map(PayloadMapper::toRelatedEntityOutput).collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(Payload payload) {
    return RelatedEntityOutput.builder().id(payload.getId()).name(payload.getName()).build();
  }
}
