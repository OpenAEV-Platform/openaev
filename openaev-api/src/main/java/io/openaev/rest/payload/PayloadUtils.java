package io.openaev.rest.payload;

import static io.openaev.database.model.Payload.PAYLOAD_EXECUTION_ARCH.arm64;
import static io.openaev.database.model.Payload.PAYLOAD_EXECUTION_ARCH.x86_64;
import static io.openaev.utils.StringUtils.duplicateString;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.payload.output_parser.OutputParserService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PayloadUtils {

  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final OutputParserService outputParserService;
  private final DetectionRemediationUtils detectionRemediationUtils;

  public static PayloadCreateInput buildPayload(@NotNull final JsonNode payloadNode) {
    PayloadCreateInput payloadCreateInput = new PayloadCreateInput();
    payloadCreateInput.setType(payloadNode.get("payload_type").textValue());
    payloadCreateInput.setName(payloadNode.get("payload_name").textValue());
    payloadCreateInput.setSource(
        Payload.PAYLOAD_SOURCE.valueOf(payloadNode.get("payload_source").textValue()));
    payloadCreateInput.setStatus(
        Payload.PAYLOAD_STATUS.valueOf(payloadNode.get("payload_status").textValue()));

    ArrayNode platformsNode = (ArrayNode) payloadNode.get("payload_platforms");
    Endpoint.PLATFORM_TYPE[] platforms = new Endpoint.PLATFORM_TYPE[platformsNode.size()];
    for (int i = 0; i < platformsNode.size(); i++) {
      platforms[i] = Endpoint.PLATFORM_TYPE.valueOf(platformsNode.get(i).textValue());
    }
    payloadCreateInput.setPlatforms(platforms);
    if (payloadNode.has("payload_description")) {
      payloadCreateInput.setDescription(payloadNode.get("payload_description").textValue());
    }
    if (payloadNode.has("command_executor")) {
      payloadCreateInput.setExecutor(payloadNode.get("command_executor").textValue());
    }
    if (payloadNode.has("command_content")) {
      payloadCreateInput.setContent(payloadNode.get("command_content").textValue());
    }
    if (payloadNode.has("payload_execution_arch")) {
      payloadCreateInput.setExecutionArch(
          Payload.PAYLOAD_EXECUTION_ARCH.valueOf(
              (payloadNode.get("payload_execution_arch").textValue())));
    }
    if (payloadNode.has("executable_file")) {
      payloadCreateInput.setExecutableFile(payloadNode.get("executable_file").textValue());
    }
    if (payloadNode.has("file_drop_file")) {
      payloadCreateInput.setFileDropFile(payloadNode.get("file_drop_file").textValue());
    }
    if (payloadNode.has("dns_resolution_hostname")) {
      payloadCreateInput.setHostname(payloadNode.get("dns_resolution_hostname").textValue());
    }

    if (payloadNode.has("payload_arguments")) {
      ArrayNode argumentsNode = (ArrayNode) payloadNode.get("payload_arguments");
      List<PayloadArgument> arguments = new ArrayList<>();
      for (JsonNode argumentNode : argumentsNode) {
        PayloadArgument argument = new PayloadArgument();
        argument.setType(argumentNode.get("type").textValue());
        argument.setKey(argumentNode.get("key").textValue());
        argument.setDefaultValue(argumentNode.get("default_value").textValue());
        argument.setDescription(argumentNode.get("description").textValue());
        arguments.add(argument);
      }
      payloadCreateInput.setArguments(arguments);
    }

    if (payloadNode.has("payload_prerequisites")) {
      ArrayNode prerequisitesNode = (ArrayNode) payloadNode.get("payload_prerequisites");
      List<PayloadPrerequisite> prerequisites = new ArrayList<>();
      for (JsonNode prerequisiteNode : prerequisitesNode) {
        PayloadPrerequisite prerequisite = new PayloadPrerequisite();
        prerequisite.setExecutor(prerequisiteNode.get("executor").textValue());
        prerequisite.setGetCommand(prerequisiteNode.get("get_command").textValue());
        prerequisite.setCheckCommand(prerequisiteNode.get("check_command").textValue());
        prerequisite.setDescription(prerequisiteNode.get("description").textValue());
        prerequisites.add(prerequisite);
      }
      payloadCreateInput.setPrerequisites(prerequisites);
    }
    if (payloadNode.has("payload_cleanup_executor")) {
      payloadCreateInput.setCleanupExecutor(
          payloadNode.get("payload_cleanup_executor").textValue());
    }
    if (payloadNode.has("payload_cleanup_command")) {
      payloadCreateInput.setCleanupCommand(payloadNode.get("payload_cleanup_command").textValue());
    }

    // TODO: tag
    payloadCreateInput.setTagIds(new ArrayList<>());
    return payloadCreateInput;
  }

  public static void validateArchitecture(String payloadType, Payload.PAYLOAD_EXECUTION_ARCH arch) {
    if (arch == null) {
      throw new BadRequestException("Payload architecture cannot be null.");
    }
    if (Executable.EXECUTABLE_TYPE.equals(payloadType) && (arch != x86_64 && arch != arm64)) {
      throw new BadRequestException("Executable architecture must be x86_64 or arm64.");
    }
  }

  public <T extends Payload> void duplicateCommonProperties(
      @NotNull final T origin, @NotNull T duplicate) {
    BeanUtils.copyProperties(
        origin,
        duplicate,
        "outputParsers",
        "tags",
        "attackPatterns",
        "arguments",
        "prerequisites",
        "detectionRemediations",
        "grants");
    duplicate.setId(null);
    duplicate.setName(duplicateString(origin.getName()));
    duplicate.setAttackPatterns(new ArrayList<>(origin.getAttackPatterns()));
    duplicate.setExternalId(null);
    duplicate.setArguments(
        Optional.ofNullable(origin.getArguments()).map(ArrayList::new).orElseGet(ArrayList::new));
    duplicate.setPrerequisites(
        Optional.ofNullable(origin.getPrerequisites())
            .map(ArrayList::new)
            .orElseGet(ArrayList::new));
    duplicate.setTags(new HashSet<>(origin.getTags()));
    duplicate.setCollector(null);
    duplicate.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    duplicate.setStatus(Payload.PAYLOAD_STATUS.UNVERIFIED);
    outputParserService.copyOutputParsersFromEntity(origin.getOutputParsers(), duplicate);

    if (eeService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      detectionRemediationUtils.copy(origin.getDetectionRemediations(), duplicate, false);
    }

    // Copy grants (each one needs to be a fully new object)
    List<Grant> grantCopies =
        origin.getGrants().stream()
            .map(
                grant -> {
                  Grant copy = new Grant();
                  copy.setName(grant.getName());
                  copy.setGroup(grant.getGroup());
                  copy.setGrantResourceType(grant.getGrantResourceType());
                  return copy;
                })
            .collect(Collectors.toList());
    duplicate.setGrants(grantCopies);
  }

  public Payload copyProperties(PayloadCreateInput payloadInput, Payload target) {
    if (payloadInput == null) {
      throw new IllegalArgumentException("Input payload cannot be null");
    }
    BeanUtils.copyProperties(
        payloadInput, target, "outputParsers", "tags", "attackPatterns", "detectionRemediations");

    outputParserService.copyOutputParsersFromInput(payloadInput.getOutputParsers(), target);
    detectionRemediationUtils.copy(payloadInput.getDetectionRemediations(), target, false);
    return target;
  }

  public Payload copyProperties(PayloadUpdateInput payloadInput, Payload target) {
    if (payloadInput == null) {
      throw new IllegalArgumentException("Input payload cannot be null");
    }

    BeanUtils.copyProperties(
        payloadInput, target, "outputParsers", "tags", "attackPatterns", "detectionRemediations");

    outputParserService.copyOutputParsersFromInput((payloadInput).getOutputParsers(), target);
    detectionRemediationUtils.copy((payloadInput).getDetectionRemediations(), target, true);
    return target;
  }

  public Payload copyProperties(
      PayloadUpsertInput payloadInput,
      Payload target,
      boolean copyId) { // false if create, true if update
    if (payloadInput == null) {
      throw new IllegalArgumentException("Input payload cannot be null");
    }

    BeanUtils.copyProperties(
        payloadInput, target, "outputParsers", "tags", "attackPatterns", "detectionRemediations");

    outputParserService.copyOutputParsersFromInput(payloadInput.getOutputParsers(), target);
    detectionRemediationUtils.copy(payloadInput.getDetectionRemediations(), target, copyId);
    return target;
  }
}
