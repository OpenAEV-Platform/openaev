package io.openaev.rest.finding;

import static io.openaev.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.inject.service.InjectService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FindingService {

  private static final String HOST = "host";

  /** Mask substituted to the secret part of a sensitive finding value. */
  public static final String MASK = "******";

  private static final int MASKING_VISIBLE_FRAGMENT_LENGTH = 2;
  private static final int MASKING_MIN_LENGTH_FOR_FRAGMENT = 5;

  private final InjectService injectService;

  private final FindingRepository findingRepository;
  private final FindingWriter findingWriter;
  private final AssetRepository assetRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;

  // -- REDACTION --

  /**
   * Redacts the value of a sensitive finding before it leaves the platform through the API. The
   * database row keeps the full cleartext value (it is needed for deduplication, correlation and
   * attack path computation): only the returned representation is masked.
   *
   * <p>Every sensitive value is masked the same way: a two character fragment is kept so an
   * operator can still tell WHICH secret was discovered when the value is already known to them,
   * without the API ever disclosing the secret itself. A value too short to keep a fragment without
   * disclosing most of it is masked entirely.
   *
   * <ul>
   *   <li>{@code jdoe:Sup3rS3cret} becomes {@code jd******}
   *   <li>{@code Sup3rS3cret} becomes {@code Su******}
   *   <li>{@code abcd} becomes {@code ******}
   * </ul>
   *
   * @param value the cleartext finding value
   * @param sensitive whether the finding holds sensitive material
   * @return the value as-is when the finding is not sensitive, its redacted form otherwise
   */
  public static String redact(final String value, final boolean sensitive) {
    if (!sensitive || value == null || value.isBlank()) {
      return value;
    }

    if (value.length() < MASKING_MIN_LENGTH_FOR_FRAGMENT) {
      return MASK;
    }
    return value.substring(0, MASKING_VISIBLE_FRAGMENT_LENGTH) + MASK;
  }

  /**
   * Redacts in place the value of a finding entity before the API serializes it. The caller must
   * run in a read only transaction, so the masked value is never flushed to the database: the row
   * keeps the cleartext value.
   */
  public Finding redactValue(@NotNull final Finding finding) {
    if (finding.isSensitive()) {
      finding.setValue(redact(finding.getValue(), true));
    }
    return finding;
  }

  // -- CRUD --

  public List<Finding> findings() {
    return fromIterable(this.findingRepository.findAll());
  }

  public Finding finding(@NotNull final String id) {
    return this.findingRepository
        .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
        .orElseThrow(() -> new EntityNotFoundException("Finding not found with id: " + id));
  }

  /**
   * Group-wide summary of a finding, deduplicated by (type, value) across every occurrence in the
   * tenant. The finding overview hero relies on this instead of the picked representative row, so
   * the first/last seen and impact counts reflect the whole group rather than one arbitrary
   * occurrence.
   */
  public FindingSummaryOutput findingSummary(@NotNull final String id) {
    Finding finding = finding(id);
    String tenantId = TenantContext.getCurrentTenant();
    ContractOutputType type = finding.getType();
    String value = finding.getValue();

    FindingRepository.FindingSeenAggregate seen =
        this.findingRepository.findSeenAggregate(type, value, tenantId);

    return FindingSummaryOutput.builder()
        .id(finding.getId())
        .type(type)
        .value(redact(value, finding.isSensitive()))
        .sensitive(finding.isSensitive())
        .firstSeen(seen != null ? seen.getFirstSeen() : finding.getCreationDate())
        .lastSeen(seen != null ? seen.getLastSeen() : finding.getUpdateDate())
        .occurrences(seen != null ? seen.getOccurrences() : 1)
        .assetsCount(this.findingRepository.countDistinctAssets(type, value, tenantId))
        .teamsCount(this.findingRepository.countDistinctTeams(type, value, tenantId))
        .usersCount(this.findingRepository.countDistinctUsers(type, value, tenantId))
        .assetGroupsCount(this.findingRepository.countDistinctAssetGroups(type, value, tenantId))
        .build();
  }

  public Finding createFinding(@NotNull final Finding finding, @NotBlank final String injectId) {
    Inject inject = this.injectService.inject(injectId);
    finding.setInject(inject);
    return this.findingRepository.save(finding);
  }

  public Finding updateFinding(@NotNull final Finding finding, @NotNull final String injectId) {
    if (!finding.getInject().getId().equals(injectId)) {
      throw new IllegalArgumentException("Inject id cannot be changed: " + injectId);
    }
    return this.findingRepository.save(finding);
  }

  public void deleteFinding(@NotNull final String id) {
    if (!this.findingRepository.existsByIdAndTenantId(id, TenantContext.getCurrentTenant())) {
      throw new EntityNotFoundException("Finding not found with id: " + id);
    }
    this.findingRepository.deleteById(id);
  }

  /**
   * Generates findings based on the provided JSON node and context. It determines whether the
   * execution is agent-based or injector-based and processes the findings accordingly.
   *
   * @param executionContext The context of the execution, containing information about whether it's
   *     an agent execution and relevant data for processing.
   * @param contractOutputContext The context of the contract output, providing details about the
   *     expected findings format and metadata.
   * @param structuredOutputNode The JSON node containing the raw data from which findings will be
   *     generated.
   * @param validator A predicate function to validate the format of each finding in the JSON node.
   * @param valueExtractor A function to extract the value for each finding from the JSON node.
   * @param assetExtractor A function to extract associated asset IDs for each finding from the JSON
   *     node (used for injector findings).
   * @param userExtractor A function to extract associated user IDs for each finding from the JSON
   *     node (used for injector findings).
   * @param teamExtractor A function to extract associated team IDs for each finding from the JSON
   *     node (used for injector findings).
   * @param sensitive Whether the findings produced by this processor hold sensitive material and
   *     must be redacted when serialized by the API.
   */
  public void generateFindings(
      ExecutionProcessingContext executionContext,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor,
      boolean sensitive) {

    if (executionContext.isAgentExecution()) {
      processAgentFindings(
          structuredOutputNode,
          executionContext.inject(),
          executionContext.agent(),
          contractOutputContext,
          executionContext.valueTargetedAssetsMap(),
          validator,
          valueExtractor,
          sensitive);
    } else {
      processInjectorFindings(
          structuredOutputNode,
          executionContext.inject(),
          contractOutputContext,
          validator,
          valueExtractor,
          assetExtractor,
          userExtractor,
          teamExtractor,
          sensitive);
    }
  }

  public void processAgentFindings(
      JsonNode structuredOutputNode,
      Inject inject,
      Agent agent,
      ContractOutputContext contractOutputContext,
      Map<String, Endpoint> valueTargetedAssetsMap,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      boolean sensitive) {

    if (structuredOutputNode == null || !structuredOutputNode.isArray()) {
      log.debug("Skipping agent findings: structuredOutputNode is null or not an array");
      return;
    }

    log.debug("Processing {} nodes for agent finding", structuredOutputNode.size());
    for (JsonNode jsonNode : structuredOutputNode) {
      if (!validator.test(jsonNode)) {
        log.error("Validation failed for node: {}", jsonNode);
        continue;
      }

      resolveAssetFromStructuredOutput(jsonNode, valueTargetedAssetsMap, agent)
          .ifPresentOrElse(
              asset ->
                  saveAgentFinding(
                      inject,
                      asset,
                      contractOutputContext,
                      valueExtractor.apply(jsonNode),
                      sensitive),
              () -> log.warn("Finding dropped: No asset match for host in {}", jsonNode));
    }
  }

  public void saveAgentFinding(
      Inject inject,
      Asset asset,
      ContractOutputContext contractOutputContext,
      String value,
      boolean sensitive) {

    findingWriter.saveCompleteFinding(
        contractOutputContext.key(),
        contractOutputContext.type().name(),
        value,
        new String[0],
        inject.getId(),
        contractOutputContext.name(),
        asset.getId(),
        contractOutputContext.tagIds(),
        sensitive,
        inject.getTenant() != null ? inject.getTenant().getId() : null);
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

  public void processInjectorFindings(
      JsonNode structuredOutputNode,
      Inject inject,
      ContractOutputContext contractOutputContext,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor,
      boolean sensitive) {

    if (structuredOutputNode == null) {
      log.debug("Skipping injector findings: structuredOutputNode is null");
      return;
    }

    List<Finding> findings =
        buildFindings(
            structuredOutputNode,
            contractOutputContext,
            validator,
            valueExtractor,
            assetExtractor,
            userExtractor,
            teamExtractor,
            sensitive);

    createFindings(findings, inject.getId());
  }

  /**
   * Persists a list of findings in the database, associating them with a specific inject.
   *
   * @param findings The list of findings to be created and persisted.
   * @param injectId The identifier of to inject to which the findings will be associated. Must not
   *     be blank.
   */
  public void createFindings(
      @NotNull final List<Finding> findings, @NotBlank final String injectId) {
    Inject inject = injectService.inject(injectId);
    findings.forEach(
        finding -> {
          finding.setInject(inject);
          finding.setTenant(inject.getTenant());
        });
    List<Finding> deduplicatedFindings = deduplicateFindings(findings);
    findingRepository.saveAll(deduplicatedFindings);
  }

  /**
   * Deduplicates a list of findings based on the unique constraint keys: value, type, and field.
   * When duplicates are found, their assets, teams and users are merged into the first occurrence.
   *
   * @param findings the raw list of findings, potentially containing duplicates
   * @return a deduplicated list with associations merged
   */
  private List<Finding> deduplicateFindings(@NotNull final List<Finding> findings) {
    Map<String, Finding> seen = new java.util.LinkedHashMap<>();
    for (Finding finding : findings) {
      String key = finding.getValue() + "|" + finding.getType() + "|" + finding.getField();
      Finding existing = seen.get(key);
      if (existing == null) {
        seen.put(key, finding);
      } else {
        log.debug(
            "Duplicate finding detected (value={}, type={}, field={}): merging associations",
            finding.getValue(),
            finding.getType(),
            finding.getField());
        if (finding.getAssets() != null) {
          List<Asset> merged =
              new ArrayList<>(existing.getAssets() != null ? existing.getAssets() : List.of());
          finding
              .getAssets()
              .forEach(
                  a -> {
                    if (!merged.contains(a)) merged.add(a);
                  });
          existing.setAssets(merged);
        }
        if (finding.getTeams() != null) {
          List<Team> merged =
              new ArrayList<>(existing.getTeams() != null ? existing.getTeams() : List.of());
          finding
              .getTeams()
              .forEach(
                  t -> {
                    if (!merged.contains(t)) merged.add(t);
                  });
          existing.setTeams(merged);
        }
        if (finding.getUsers() != null) {
          List<User> merged =
              new ArrayList<>(existing.getUsers() != null ? existing.getUsers() : List.of());
          finding
              .getUsers()
              .forEach(
                  u -> {
                    if (!merged.contains(u)) merged.add(u);
                  });
          existing.setUsers(merged);
        }
      }
    }
    return new ArrayList<>(seen.values());
  }

  public List<Finding> buildFindings(
      JsonNode structuredOutputNode,
      ContractOutputContext contractOutputContext,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor,
      boolean sensitive) {

    if (contractOutputContext.isMultiple() && structuredOutputNode.isArray()) {
      List<Finding> findings = new ArrayList<>();
      for (JsonNode node : structuredOutputNode) {
        // Skip malformed entries instead of aborting the whole batch: a single bad finding must not
        // throw out of the execution callback, which would prevent the COMPLETE trace from being
        // saved and leave the inject stuck (ultimately flipped to ERROR).
        if (!validator.test(node)) {
          log.warn("Skipping malformed {} finding: {}", contractOutputContext.type(), node);
          continue;
        }
        findings.add(
            buildSingleFinding(
                node,
                contractOutputContext,
                validator,
                valueExtractor,
                assetExtractor,
                userExtractor,
                teamExtractor,
                sensitive));
      }
      return findings;
    }

    return List.of(
        buildSingleFinding(
            structuredOutputNode,
            contractOutputContext,
            validator,
            valueExtractor,
            assetExtractor,
            userExtractor,
            teamExtractor,
            sensitive));
  }

  private Finding buildSingleFinding(
      JsonNode structuredOutputNode,
      ContractOutputContext contractOutputContext,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor,
      boolean sensitive) {

    if (!validator.test(structuredOutputNode)) {
      throw new IllegalArgumentException(
          "Finding not correctly formatted: " + structuredOutputNode);
    }

    Finding finding = FindingUtils.createFinding(contractOutputContext);
    finding.setValue(valueExtractor.apply(structuredOutputNode));
    finding.setSensitive(sensitive);
    return linkFinding(structuredOutputNode, finding, assetExtractor, userExtractor, teamExtractor);
  }

  private Finding linkFinding(
      JsonNode structuredOutputNode,
      Finding finding,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor) {

    List<String> assetIds = assetExtractor.apply(structuredOutputNode);
    if (!assetIds.isEmpty()) {
      finding.setAssets(fetchEntities(assetIds, assetRepository::findById));
    }

    List<String> teamIds = teamExtractor.apply(structuredOutputNode);
    if (!teamIds.isEmpty()) {
      finding.setTeams(fetchEntities(teamIds, teamRepository::findById));
    }

    List<String> userIds = userExtractor.apply(structuredOutputNode);
    if (!userIds.isEmpty()) {
      finding.setUsers(fetchEntities(userIds, userRepository::findById));
    }

    return finding;
  }

  private <T> List<T> fetchEntities(List<String> ids, Function<String, Optional<T>> finder) {
    return ids.stream().map(finder).filter(Optional::isPresent).map(Optional::get).toList();
  }
}
