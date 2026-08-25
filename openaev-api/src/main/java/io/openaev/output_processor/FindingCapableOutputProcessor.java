package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Abstract base class for output processors that are capable of generating findings. */
@Slf4j
public abstract class FindingCapableOutputProcessor extends AbstractOutputProcessor {

  /** Sensitivity decision of a finding type, hardcoded by the processors that produce secrets. */
  protected static final boolean SENSITIVE = true;

  protected final FindingService findingService;

  /**
   * Whether the findings produced by this processor hold sensitive material (secrets, hashes...).
   * The sensitivity is a property of the finding TYPE, decided once per processor, and is persisted
   * on every finding it creates so the API can redact the value when serializing it. The database
   * keeps the cleartext value.
   */
  @Getter private final boolean sensitive;

  /**
   * Declares a processor whose findings hold no sensitive material, which is the case of every
   * finding type but credentials. A processor producing secrets must use the constructor taking an
   * explicit sensitivity and pass {@link #SENSITIVE}. The full type - sensitivity matrix is
   * asserted by {@code OutputProcessorIntegrationTest}, so adding a sensitive type there is a
   * deliberate, reviewed decision.
   */
  protected FindingCapableOutputProcessor(
      ContractOutputType type,
      ContractOutputTechnicalType technicalType,
      List<ContractOutputField> fields,
      FindingService findingService) {
    this(type, technicalType, fields, findingService, false);
  }

  protected FindingCapableOutputProcessor(
      ContractOutputType type,
      ContractOutputTechnicalType technicalType,
      List<ContractOutputField> fields,
      FindingService findingService,
      boolean sensitive) {
    super(type, technicalType, fields);
    this.findingService = findingService;
    this.sensitive = sensitive;
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
        this::toFindingAssets,
        this::toFindingTeams,
        this::toFindingUsers,
        this.sensitive);
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

  /** Extract asset IDs from JSON node. Default returns empty list. */
  public List<String> toFindingAssets(JsonNode jsonNode) {
    log.debug("Processor {} does not implement toFindingAssets, returning empty list", type);
    return Collections.emptyList();
  }

  /** Extract user IDs from JSON node. Default returns empty list. */
  public List<String> toFindingUsers(JsonNode jsonNode) {
    log.debug("Processor {} does not implement toFindingUsers, returning empty list", type);
    return Collections.emptyList();
  }

  /** Extract team IDs from JSON node. Default returns empty list. */
  public List<String> toFindingTeams(JsonNode jsonNode) {
    log.debug("Processor {} does not implement toFindingTeams, returning empty list", type);
    return Collections.emptyList();
  }
}
