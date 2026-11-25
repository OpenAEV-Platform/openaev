package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.finding.identity.LocationIdentity;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import io.openaev.finding.identity.LocationIdentity.UnknownLocation;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/** Abstract base class for output processors that are capable of generating findings. */
public abstract class FindingCapableOutputProcessor extends AbstractOutputProcessor {

  public static final String ASSET_ID = "asset_id";

  protected final FindingService findingService;

  protected FindingCapableOutputProcessor(
      ContractOutputType type,
      ContractOutputTechnicalType technicalType,
      List<ContractOutputField> fields,
      FindingService findingService) {
    super(type, technicalType, fields);
    this.findingService = findingService;
  }

  /**
   * Processes the structured output by generating findings via {@link FindingService}, then calls
   * {@link #afterFindings} to allow subclasses to perform additional steps (e.g. expectation
   * matching) without overriding this method entirely.
   */
  @Override
  public final void process(
      ExecutionProcessingContext executionContext,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    findingService.generateFindings(
        executionContext,
        contractOutputContext,
        structuredOutputNode,
        this::validate,
        this::toFindingValue,
        this::toFindingLocations);
    afterFindings(executionContext, structuredOutputNode);
  }

  /**
   * Hook called after findings are generated. Override to perform additional processing such as
   * expectation matching without needing to override {@link #process} entirely.
   */
  protected void afterFindings(
      ExecutionProcessingContext executionContext, JsonNode structuredOutputNode) {
    // no-op by default
  }

  /** Convert JSON node to finding value string. Subclasses must provide a meaningful value. */
  public abstract String toFindingValue(JsonNode jsonNode);

  /**
   * Where the finding was detected: one asset location per id of {@code asset_id} (text or array),
   * the unknown location when there is none. An id that is null, blank or the text {@code "null"}
   * counts as none: nuclei sends {@code [""]} for the hosts outside its asset map, and nmap in
   * manual mode sends {@code null}.
   */
  public List<LocationIdentity> toFindingLocations(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get(ASSET_ID);
    List<JsonNode> idNodes =
        assetIdNode == null
            ? List.of()
            : assetIdNode.isArray()
                ? StreamSupport.stream(assetIdNode.spliterator(), false).toList()
                : List.of(assetIdNode);
    List<LocationIdentity> locations = new ArrayList<>();
    for (JsonNode idNode : idNodes) {
      String assetId = idNode.isNull() ? null : idNode.asText().strip();
      if (assetId != null && !assetId.isEmpty() && !"null".equals(assetId)) {
        locations.add(new AssetLocation(assetId));
      }
    }
    return locations.isEmpty() ? List.of(UnknownLocation.INSTANCE) : locations;
  }
}
