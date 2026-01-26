package io.openaev.utils;

import static io.openaev.database.model.Command.COMMAND_TYPE;
import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.Executable.EXECUTABLE_TYPE;
import static io.openaev.database.model.FileDrop.FILE_DROP_TYPE;
import static io.openaev.database.model.NetworkTraffic.NETWORK_TRAFFIC_TYPE;
import static io.openaev.utils.ExpectationUtils.isAssetExpectation;
import static io.openaev.utils.ExpectationUtils.isAssetGroupExpectation;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.helper.ObjectMapperHelper;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * Component providing utility methods for inject operations.
 *
 * <p>Handles payload extraction, expectation filtering, row validation for imports, and inject
 * duplication. This component is central to inject processing workflows including execution,
 * import, and cloning operations.
 *
 * @see io.openaev.database.model.Inject
 * @see io.openaev.database.model.StatusPayload
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class InjectUtils {

  /**
   * Extracts the payload information from an inject.
   *
   * <p>Determines the appropriate payload based on the inject's execution status or injector
   * contract. Supports multiple payload types including:
   *
   * <ul>
   *   <li>Command payloads (shell commands)
   *   <li>Executable payloads (binary files)
   *   <li>File drop payloads (file deployment)
   *   <li>DNS resolution payloads (DNS queries)
   *   <li>Network traffic payloads (network operations)
   * </ul>
   *
   * <p>If the inject has been executed, returns the saved payload output. Otherwise, constructs the
   * payload from the injector contract configuration.
   *
   * @param inject the inject to extract payload from
   * @return the status payload, or {@code null} if no payload can be determined
   */
  public StatusPayload getStatusPayloadFromInject(final Inject inject) {
    if (inject == null) {
      return null;
    }

    if (inject.getStatus().isPresent() && inject.getStatus().get().getPayloadOutput() != null) {
      // Commands lines saved because inject has been executed
      return inject.getStatus().get().getPayloadOutput();
    }

    InjectorContract injectorContract = inject.getInjectorContract().orElse(null);

    if (injectorContract == null || inject.getInjectorContract().get().getPayload() == null) {
      return null;
    }

    Payload payload = injectorContract.getPayload();

    if (COMMAND_TYPE.equals(injectorContract.getPayload().getType())) {
      // Inject has a command payload
      Command payloadCommand = (Command) Hibernate.unproxy(payload);
      PayloadCommandBlock payloadCommandBlock =
          new PayloadCommandBlock(payloadCommand.getExecutor(), payloadCommand.getContent(), null);
      if (payloadCommand.getCleanupCommand() != null) {
        payloadCommandBlock.setCleanupCommand(List.of(payloadCommand.getCleanupCommand()));
      }
      return new StatusPayload(
          payloadCommand.getName(),
          payloadCommand.getDescription(),
          COMMAND_TYPE,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          payloadCommand.getExternalId(),
          payloadCommand.getPrerequisites(),
          payloadCommand.getArguments(),
          List.of(payloadCommandBlock),
          payloadCommand.getCleanupExecutor());
    }
    if (EXECUTABLE_TYPE.equals(injectorContract.getPayload().getType())) {
      // Inject has a command payload
      Executable payloadExecutable = (Executable) Hibernate.unproxy(payload);
      return new StatusPayload(
          payloadExecutable.getName(),
          payloadExecutable.getDescription(),
          EXECUTABLE_TYPE,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          payloadExecutable.getExecutableFile(),
          null,
          null,
          null,
          emptyList(),
          null);
    }
    if (FILE_DROP_TYPE.equals(injectorContract.getPayload().getType())) {
      // Inject has a command payload
      FileDrop payloadFileDrop = (FileDrop) Hibernate.unproxy(payload);
      return new StatusPayload(
          payloadFileDrop.getName(),
          payloadFileDrop.getDescription(),
          FILE_DROP_TYPE,
          null,
          null,
          null,
          null,
          null,
          null,
          payloadFileDrop.getFileDropFile(),
          null,
          null,
          null,
          null,
          emptyList(),
          null);
    }
    if (DNS_RESOLUTION_TYPE.equals(injectorContract.getPayload().getType())) {
      // Inject has a command payload
      DnsResolution payloadDnsResolution = (DnsResolution) Hibernate.unproxy(payload);
      return new StatusPayload(
          payloadDnsResolution.getName(),
          payloadDnsResolution.getDescription(),
          DNS_RESOLUTION_TYPE,
          null,
          null,
          null,
          null,
          null,
          payloadDnsResolution.getHostname(),
          null,
          null,
          null,
          null,
          null,
          emptyList(),
          null);
    }
    if (NETWORK_TRAFFIC_TYPE.equals(injectorContract.getPayload().getType())) {
      // Inject has a command payload
      NetworkTraffic payloadNetworkTraffic = (NetworkTraffic) Hibernate.unproxy(payload);
      return new StatusPayload(
          payloadNetworkTraffic.getName(),
          payloadNetworkTraffic.getDescription(),
          NETWORK_TRAFFIC_TYPE,
          payloadNetworkTraffic.getProtocol(),
          payloadNetworkTraffic.getPortDst(),
          payloadNetworkTraffic.getPortSrc(),
          payloadNetworkTraffic.getIpDst(),
          payloadNetworkTraffic.getIpSrc(),
          null,
          null,
          null,
          null,
          null,
          null,
          emptyList(),
          null);
    }

    return null;
  }

  /**
   * Retrieves the primary expectations from an inject.
   *
   * <p>Primary expectations are those directly associated with the inject's targets (teams, assets,
   * or asset groups). This filters out derived or secondary expectations that may exist for
   * sub-targets.
   *
   * @param inject the inject to get expectations from
   * @return a list of expectations matching the inject's direct targets
   */
  public List<InjectExpectation> getPrimaryExpectations(Inject inject) {
    List<String> firstIds = new ArrayList<>();

    firstIds.addAll(inject.getTeams().stream().map(Team::getId).toList());
    firstIds.addAll(inject.getAssets().stream().map(Asset::getId).toList());
    firstIds.addAll(inject.getAssetGroups().stream().map(AssetGroup::getId).toList());

    // Reject expectations if none of the team, asset, or assetGroup IDs exist in firstIds
    return inject.getExpectations().stream()
        .filter(
            expectation -> {
              boolean teamMatch =
                  expectation.getTeam() != null && firstIds.contains(expectation.getTeam().getId());
              boolean assetMatch =
                  isAssetExpectation(expectation)
                      && firstIds.contains(expectation.getAsset().getId());
              boolean assetGroupMatch =
                  isAssetGroupExpectation(expectation)
                      && firstIds.contains(expectation.getAssetGroup().getId());
              return teamMatch || assetMatch || assetGroupMatch;
            })
        .collect(Collectors.toList());
  }

  /**
   * Checks if an Excel row is empty or contains only blank cells.
   *
   * <p>Used during XLS import operations to skip empty rows. A row is considered empty if:
   *
   * <ul>
   *   <li>The row is null
   *   <li>The row has no cells
   *   <li>All cells are blank or contain only whitespace
   * </ul>
   *
   * @param row the Excel row to check
   * @return {@code true} if the row is empty, {@code false} otherwise
   */
  public static boolean checkIfRowIsEmpty(Row row) {
    if (row == null) {
      return true;
    }
    if (row.getLastCellNum() <= 0) {
      return true;
    }
    for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
      Cell cell = row.getCell(cellNum);
      if (cell != null
          && cell.getCellType() != CellType.BLANK
          && StringUtils.isNotBlank(cell.toString())) {
        return false;
      }
    }
    return true;
  }

  /** Shared ObjectMapper instance for JSON processing in inject duplication. */
  private static final ObjectMapper STATIC_MAPPER = ObjectMapperHelper.openAEVJsonMapper();

  /**
   * Creates a deep copy of an inject.
   *
   * <p>Duplicates all properties of the source inject including content, teams, assets, asset
   * groups, tags, and dependencies. The content is deep-copied to ensure modifications to the
   * duplicate don't affect the original.
   *
   * <p>The duplicated inject maintains references to the same exercise/scenario and injector
   * contract as the original.
   *
   * @param injectOrigin the inject to duplicate (must not be null)
   * @return a new inject instance with copied properties
   * @throws RuntimeException if the content cannot be serialized/deserialized
   */
  public static Inject duplicateInject(@NotNull Inject injectOrigin) {
    Inject duplicatedInject = new Inject();
    duplicatedInject.setUser(injectOrigin.getUser());
    duplicatedInject.setTitle(injectOrigin.getTitle());
    duplicatedInject.setDescription(injectOrigin.getDescription());
    try {
      ObjectNode content =
          STATIC_MAPPER.readValue(
              STATIC_MAPPER.writeValueAsString(injectOrigin.getContent()), ObjectNode.class);
      duplicatedInject.setContent(content);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    duplicatedInject.setAllTeams(injectOrigin.isAllTeams());
    duplicatedInject.setTeams(injectOrigin.getTeams().stream().toList());
    duplicatedInject.setEnabled(injectOrigin.isEnabled());
    duplicatedInject.setDependsDuration(injectOrigin.getDependsDuration());
    if (injectOrigin.getDependsOn() != null) {
      duplicatedInject.setDependsOn(injectOrigin.getDependsOn().stream().toList());
    }
    duplicatedInject.setCountry(injectOrigin.getCountry());
    duplicatedInject.setCity(injectOrigin.getCity());
    duplicatedInject.setInjectorContract(injectOrigin.getInjectorContract().orElse(null));
    duplicatedInject.setAssetGroups(injectOrigin.getAssetGroups().stream().toList());
    duplicatedInject.setAssets(injectOrigin.getAssets().stream().toList());
    duplicatedInject.setCommunications(injectOrigin.getCommunications().stream().toList());
    duplicatedInject.setTags(new HashSet<>(injectOrigin.getTags()));

    duplicatedInject.setExercise(injectOrigin.getExercise());
    duplicatedInject.setScenario(injectOrigin.getScenario());
    return duplicatedInject;
  }
}
