package io.openaev.rest.finding;

import static io.openaev.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.finding.identity.DetectedFinding;
import io.openaev.finding.identity.LocationIdentity;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.service.finding.FindingRecordingService;
import io.openaev.utils.mapper.FindingMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FindingService {

  private static final String HOST = "host";

  private final FindingRepository findingRepository;
  private final FindingMapper findingMapper;
  private final FindingRecordingService findingRecordingService;

  // -- CRUD --

  /**
   * No production caller: only tests reach this. Kept because two tenant-isolation suites use it as
   * their subject, but it is a trap for the next person - an unscoped {@code findAll} on an
   * activated table returns nothing in production. Tracked for removal with its test callers.
   */
  public List<Finding> findings() {
    return fromIterable(this.findingRepository.findAll());
  }

  public Finding finding(@NotNull final String id) {
    return this.findingRepository
        .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
        .orElseThrow(() -> new EntityNotFoundException("Finding not found with id: " + id));
  }

  /**
   * Resolves the group-wide data backing the summary of a finding - the group being every
   * occurrence sharing its (type, value) in the tenant - and delegates the assembly of the output
   * to the mapper.
   */
  public FindingSummaryOutput findingSummary(@NotNull final String id) {
    Finding finding = finding(id);
    String tenantId = TenantContext.getCurrentTenant();
    ContractOutputType type = finding.getType();
    String displayValue = finding.getValueDisplay();

    FindingRepository.FindingSeenAggregate seen =
        this.findingRepository.findSeenAggregate(type, displayValue, tenantId);

    FindingMapper.FindingImpactCounts counts =
        new FindingMapper.FindingImpactCounts(
            this.findingRepository.countDistinctAssets(type, displayValue, tenantId),
            this.findingRepository.countDistinctTeams(type, displayValue, tenantId),
            this.findingRepository.countDistinctUsers(type, displayValue, tenantId),
            this.findingRepository.countDistinctAssetGroups(type, displayValue, tenantId));

    return this.findingMapper.toFindingSummaryOutput(finding, seen, counts);
  }

  /**
   * Records what an inject detected, retried once: two batches creating the same location, finding
   * or tag at once collide on its unique key, and the retry finds what the other one created.
   */
  public List<Finding> recordFindingsByInject(
      @NotNull final TxCtx ctx,
      @NotNull final Inject inject,
      @NotNull final List<DetectedFinding> detections) {
    try {
      return findingRecordingService.recordFindingsByInject(ctx, inject, detections);
    } catch (DataIntegrityViolationException concurrentBatch) {
      return findingRecordingService.recordFindingsByInject(ctx, inject, detections);
    }
  }

  /**
   * Records the findings of a structured output: an agent execution locates them on the asset whose
   * host they name (its own asset when none is named), an injector on the assets of the output.
   * Entries that fail {@code validator} are skipped: one malformed finding must not throw out of the
   * execution callback, which would leave the inject stuck.
   *
   * @param executionContext The context of the execution, containing information about whether it's
   *     an agent execution and relevant data for processing.
   * @param contractOutputContext The context of the contract output, providing details about the
   *     expected findings format and metadata.
   * @param structuredOutputNode The JSON node containing the raw data from which findings will be
   *     generated.
   * @param validator A predicate function to validate the format of each finding in the JSON node.
   * @param valueExtractor A function to extract the value for each finding from the JSON node.
   * @param locationExtractor A function to extract the locations (assets or users) of each finding
   *     from the JSON node (used for injector findings).
   */
  public void generateFindings(
      ExecutionProcessingContext executionContext,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<LocationIdentity>> locationExtractor) {
    if (structuredOutputNode == null) {
      log.debug("Skipping findings: structuredOutputNode is null");
      return;
    }
    List<DetectedFinding> detections =
        executionContext.isAgentExecution()
            ? toAgentDetections(
                structuredOutputNode,
                executionContext.agent(),
                executionContext.valueTargetedAssetsMap(),
                contractOutputContext,
                validator,
                valueExtractor)
            : toInjectorDetections(
                structuredOutputNode,
                contractOutputContext,
                validator,
                valueExtractor,
                locationExtractor);
    if (detections.isEmpty()) {
      return;
    }
    Inject inject = executionContext.inject();
    recordFindingsByInject(TxCtx.forTenant(inject.getTenant().getId()), inject, detections);
  }

  private List<DetectedFinding> toAgentDetections(
      JsonNode structuredOutputNode,
      Agent agent,
      Map<String, Endpoint> valueTargetedAssetsMap,
      ContractOutputContext contractOutputContext,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor) {
    if (!structuredOutputNode.isArray()) {
      log.debug("Skipping agent findings: structuredOutputNode is not an array");
      return List.of();
    }
    List<DetectedFinding> detections = new ArrayList<>();
    for (JsonNode node : structuredOutputNode) {
      if (!validator.test(node)) {
        log.error("Validation failed for node: {}", node);
        continue;
      }
      resolveAssetFromStructuredOutput(node, valueTargetedAssetsMap, agent)
          .ifPresentOrElse(
              asset ->
                  detections.add(
                      detection(
                          contractOutputContext,
                          valueExtractor.apply(node),
                          new AssetLocation(asset.getId()))),
              () -> log.warn("Finding dropped: No asset match for host in {}", node));
    }
    return detections;
  }

  private Optional<Asset> resolveAssetFromStructuredOutput(
      JsonNode structuredOutput, Map<String, Endpoint> valueTargetedAssetsMap, Agent sourceAgent) {
    if (valueTargetedAssetsMap.isEmpty() || !structuredOutput.has(HOST)) {
      return Optional.of(sourceAgent.getAsset());
    }

    String host = structuredOutput.get(HOST).asText();
    return valueTargetedAssetsMap.keySet().stream()
        .filter(host::contains)
        .findFirst()
        .map(valueTargetedAssetsMap::get);
  }

  // One detection per location of each valid entry
  private List<DetectedFinding> toInjectorDetections(
      JsonNode structuredOutputNode,
      ContractOutputContext contractOutputContext,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<LocationIdentity>> locationExtractor) {
    List<JsonNode> nodes =
        contractOutputContext.isMultiple() && structuredOutputNode.isArray()
            ? StreamSupport.stream(structuredOutputNode.spliterator(), false).toList()
            : List.of(structuredOutputNode);
    List<DetectedFinding> detections = new ArrayList<>();
    for (JsonNode node : nodes) {
      if (!validator.test(node)) {
        log.warn("Skipping malformed {} finding: {}", contractOutputContext.type(), node);
        continue;
      }
      String value = valueExtractor.apply(node);
      for (LocationIdentity location : locationExtractor.apply(node)) {
        detections.add(detection(contractOutputContext, value, location));
      }
    }
    return detections;
  }

  private static DetectedFinding detection(
      ContractOutputContext contractOutputContext, String value, LocationIdentity location) {
    return DetectedFinding.builder()
        .type(contractOutputContext.type())
        .value(value)
        .field(contractOutputContext.key())
        .name(contractOutputContext.name())
        .location(location)
        .tagIds(new HashSet<>(Arrays.asList(contractOutputContext.tagIds())))
        .build();
  }
}
