package io.openaev.rest.attack_pattern.service;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.attack_pattern.dto.AttackPatternCoverageOutput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.api.StructuralHistogramRuntime;
import io.openaev.engine.api.StructuralHistogramWidget;
import io.openaev.engine.api.WidgetConfigurationWithSeries;
import io.openaev.engine.query.EsSeries;
import io.openaev.engine.query.EsSeriesData;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.SecurityCoverageUtils;
import io.openaev.utils.mapper.RawUserAuthMapper;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttackPatternService {

  /**
   * Per-file size limit for AI uploads (in bytes). Files larger than this are rejected to avoid OOM
   * when base64-encoding the payload to XTM One. The global multipart limit ({@code
   * spring.servlet.multipart.max-file-size}) is much higher and intended for documents, not AI
   * prompts.
   */
  private static final long AI_UPLOAD_MAX_BYTES_PER_FILE = 5L * 1024 * 1024;

  @Resource protected ObjectMapper mapper;

  private final Environment env;
  private final AttackPatternRepository attackPatternRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;
  private final EnterpriseEditionService enterpriseEditionService;
  private final RestTemplate restTemplate;
  private final SecurityCoverageUtils securityCoverageUtils;
  private final XtmOneConfig xtmOneConfig;
  private final XtmOneClient xtmOneClient;
  private final ExerciseRepository exerciseRepository;
  private final UserRepository userRepository;
  private final EngineService engineService;
  private final RawUserAuthMapper rawUserAuthMapper;
  private final AiMetricCollector aiMetricCollector;

  /**
   * Call the TTP Extraction AI Webservice to analyze files and text input.
   *
   * @param files List of files to be analyzed, maximum 5 files.
   * @param text Text input to be analyzed.
   * @return Response body from the TTP Extraction AI Webservice, expected to be a JSON array
   * @throws IOException
   */
  private String callTTPExtractionAIWebservice(List<MultipartFile> files, String text)
      throws IOException {
    String url = Objects.requireNonNull(env.getProperty("ttp.extraction.ai.webservice.url"));
    String certificate = enterpriseEditionService.getEnterpriseEditionLicensePem();
    if (certificate == null || certificate.isBlank()) {
      throw new IllegalStateException("Enterprise Edition is not available");
    }
    String encodedCertificate =
        Base64.getEncoder().encodeToString(certificate.getBytes(StandardCharsets.UTF_8));

    // Set up the headers for the request
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.add("X-OpenAEV-Certificate", encodedCertificate);

    // Set up the request body
    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    for (MultipartFile file : files) {
      ByteArrayResource resource =
          new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
              return file.getOriginalFilename();
            }
          };
      bodyBuilder.part("files", resource);
    }
    bodyBuilder.part("text", text);

    HttpEntity<MultiValueMap<String, HttpEntity<?>>> requestEntity =
        new HttpEntity<>(bodyBuilder.build(), headers);

    // Make the POST request to the TTP Extraction AI Webservice
    ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

    if (response.getStatusCode().isError()) {
      log.error("Request to TTP Extraction AI Webservice failed: {}", response.getBody());
      throw new RestClientException(
          "Request to TTP Extraction AI Webservice failed: " + response.getBody());
    }
    return response.getBody();
  }

  /**
   * Find external attack pattern from Id.
   *
   * @param attackPatternId Id
   * @return attackPattern
   * @throws IOException
   */
  public AttackPattern findById(String attackPatternId) {
    return this.attackPatternRepository
        .findById(attackPatternId)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Attack pattern not found with id: " + attackPatternId));
  }

  // -- GLOBAL MITRE ATT&CK COVERAGE --

  private static final String COVERAGE_PREVENTION_SUCCESS = "PREVENTION_SUCCESS";
  private static final String COVERAGE_PREVENTION_FAILED = "PREVENTION_FAILED";
  private static final String COVERAGE_DETECTION_SUCCESS = "DETECTION_SUCCESS";
  private static final String COVERAGE_DETECTION_FAILED = "DETECTION_FAILED";

  /** High terms-bucket cap so every attack pattern is returned (the default cap is only 100). */
  private static final int COVERAGE_BUCKET_CAP = 10_000;

  /**
   * Upper bound for the {@code latest} scoping parameter. Keeps the request cost bounded: a very
   * large value would otherwise produce a huge SQL {@code LIMIT} and feed an oversized
   * simulation-id list into the Elasticsearch filter.
   */
  private static final int COVERAGE_LATEST_MAX = 1_000;

  /**
   * Compute the tenant-wide MITRE ATT&CK coverage matrix.
   *
   * <p>Uses the very same Elasticsearch aggregation as the home {@code security-coverage} matrix: a
   * structural histogram on {@code base_attack_patterns_side} over the {@code expectation-inject}
   * documents, with one series per (PREVENTION|DETECTION) x (SUCCESS|FAILED) combination, evaluated
   * through {@link EngineService#multiTermHistogram}. The query is tenant- and ACL-scoped
   * automatically and, by default, spans every simulation - so the numbers match the home matrix.
   *
   * @param latest when non-null and positive, restrict the aggregation to the latest N finished
   *     simulations by end date (capped at {@value #COVERAGE_LATEST_MAX}); when null, aggregate
   *     across all simulations like the home matrix
   * @return the coverage entries sorted by attack pattern external id (patterns without any
   *     prevention or detection result are excluded)
   */
  @Transactional(readOnly = true)
  public List<AttackPatternCoverageOutput> getGlobalCoverage(Integer latest) {
    List<String> simulationIds = resolveLatestSimulationIds(latest);
    if (simulationIds != null && simulationIds.isEmpty()) {
      // latest scoping was requested but no finished simulation exists -> nothing to aggregate
      return List.of();
    }

    RawUserAuth user =
        rawUserAuthMapper.toRawUserAuth(userRepository.getUserWithAuth(currentUser().getId()));

    StructuralHistogramWidget widget = new StructuralHistogramWidget();
    widget.setField("base_attack_patterns_side");
    widget.setDateAttribute("base_created_at");
    widget.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    widget.setLimit(COVERAGE_BUCKET_CAP);
    widget.setSeries(
        List.of(
            coverageSeries(
                COVERAGE_PREVENTION_SUCCESS,
                BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                simulationIds),
            coverageSeries(
                COVERAGE_PREVENTION_FAILED,
                BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                BaseInjectExpectation.EXPECTATION_STATUS.FAILED,
                simulationIds),
            coverageSeries(
                COVERAGE_DETECTION_SUCCESS,
                BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                simulationIds),
            coverageSeries(
                COVERAGE_DETECTION_FAILED,
                BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                BaseInjectExpectation.EXPECTATION_STATUS.FAILED,
                simulationIds)));

    List<EsSeries> series =
        engineService.multiTermHistogram(
            user, new StructuralHistogramRuntime(widget, Map.of(), Map.of()));

    // attackPatternId -> [preventionSuccess, preventionFailed, detectionSuccess, detectionFailed]
    Map<String, long[]> countsByAttackPattern = new HashMap<>();
    for (EsSeries serie : series) {
      int index = seriesIndex(serie.getLabel());
      if (index < 0) {
        continue;
      }
      for (EsSeriesData data : serie.getData()) {
        countsByAttackPattern.computeIfAbsent(data.getKey(), k -> new long[4])[index] +=
            data.getValue();
      }
    }
    if (countsByAttackPattern.isEmpty()) {
      return List.of();
    }

    // Fetch the attack patterns together with their kill chain phases in a single query to avoid
    // an N+1 pattern (killChainPhases is a LAZY @ManyToMany without SUBSELECT fetching).
    Map<String, AttackPattern> attackPatternsById =
        attackPatternRepository
            .findAllByIdInWithKillChainPhases(countsByAttackPattern.keySet())
            .stream()
            .collect(Collectors.toMap(AttackPattern::getId, Function.identity()));

    List<AttackPatternCoverageOutput> coverage = new ArrayList<>();
    countsByAttackPattern.forEach(
        (attackPatternId, counts) -> {
          AttackPattern attackPattern = attackPatternsById.get(attackPatternId);
          if (attackPattern == null) {
            return;
          }
          long preventionSuccess = counts[0];
          long preventionTotal = counts[0] + counts[1];
          long detectionSuccess = counts[2];
          long detectionTotal = counts[2] + counts[3];
          if (preventionTotal == 0 && detectionTotal == 0) {
            return;
          }
          coverage.add(
              new AttackPatternCoverageOutput(
                  attackPattern.getId(),
                  attackPattern.getExternalId(),
                  attackPattern.getName(),
                  attackPattern.getKillChainPhases().stream()
                      .map(
                          phase ->
                              new AttackPatternCoverageOutput.KillChainPhaseCoverage(
                                  phase.getId(),
                                  phase.getName(),
                                  phase.getExternalId(),
                                  phase.getOrder()))
                      .toList(),
                  preventionSuccess,
                  preventionTotal,
                  detectionSuccess,
                  detectionTotal));
        });

    coverage.sort(
        Comparator.comparing(
            AttackPatternCoverageOutput::attackPatternExternalId,
            Comparator.nullsLast(Comparator.naturalOrder())));
    return coverage;
  }

  /**
   * Resolve the latest N finished simulation ids used to scope the coverage, or {@code null} to
   * aggregate across all simulations (home-identical behaviour). {@code latest} is clamped to
   * {@link #COVERAGE_LATEST_MAX} to keep the query and the downstream Elasticsearch filter bounded.
   */
  private List<String> resolveLatestSimulationIds(Integer latest) {
    if (latest == null || latest <= 0) {
      return null;
    }
    int cappedLatest = Math.min(latest, COVERAGE_LATEST_MAX);
    // The LIMIT is applied at the database so only the requested N rows are fetched.
    return exerciseRepository.findLatestExerciseIdsByStatus(
        ExerciseStatus.FINISHED.name(), cappedLatest);
  }

  private static WidgetConfigurationWithSeries.Series coverageSeries(
      String name,
      BaseInjectExpectation.EXPECTATION_TYPE type,
      BaseInjectExpectation.EXPECTATION_STATUS status,
      List<String> simulationIds) {
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    List<Filters.Filter> filters = new ArrayList<>();
    filters.add(
        coverageFilter("base_entity", Filters.FilterMode.and, List.of("expectation-inject")));
    filters.add(
        coverageFilter("inject_expectation_type", Filters.FilterMode.and, List.of(type.name())));
    filters.add(
        coverageFilter(
            "inject_expectation_status", Filters.FilterMode.and, List.of(status.name())));
    if (simulationIds != null && !simulationIds.isEmpty()) {
      filters.add(coverageFilter("base_simulation_side", Filters.FilterMode.or, simulationIds));
    }
    filterGroup.setFilters(filters);

    WidgetConfigurationWithSeries.Series serie = new WidgetConfigurationWithSeries.Series();
    serie.setName(name);
    serie.setFilter(filterGroup);
    return serie;
  }

  private static Filters.Filter coverageFilter(
      String key, Filters.FilterMode mode, List<String> values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setMode(mode);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(values);
    return filter;
  }

  private static int seriesIndex(String label) {
    if (label == null) {
      return -1;
    }
    return switch (label) {
      case COVERAGE_PREVENTION_SUCCESS -> 0;
      case COVERAGE_PREVENTION_FAILED -> 1;
      case COVERAGE_DETECTION_SUCCESS -> 2;
      case COVERAGE_DETECTION_FAILED -> 3;
      default -> -1;
    };
  }

  /**
   * Extract external attack pattern IDs from the response body of the TTP Extraction AI Webservice.
   *
   * @param responseBody The response body from the TTP Extraction AI Webservice, expected to be a
   *     JSON array
   * @return Set of external attack pattern IDs extracted from the response
   * @throws IOException
   */
  private Set<String> extractExternalAttackPatternIdsFromResponse(String responseBody)
      throws IOException {
    JsonNode root = mapper.readTree(responseBody);
    Set<String> externalAttackPatternIds = new HashSet<>();

    if (root == null || !root.isObject()) {
      return externalAttackPatternIds;
    }

    root.fields()
        .forEachRemaining(
            entry -> {
              JsonNode chunks = entry.getValue();
              if (chunks == null || !chunks.isArray()) {
                return;
              }
              chunks.forEach(
                  chunk -> {
                    JsonNode predictions = chunk.get("predictions");
                    if (predictions != null && predictions.isObject()) {
                      predictions.fieldNames().forEachRemaining(externalAttackPatternIds::add);
                    }
                  });
            });
    return externalAttackPatternIds;
  }

  public List<AttackPattern> getAttackPatternsByExternalIds(Set<String> ids) {
    if (ids.isEmpty()) {
      return Collections.emptyList();
    }
    return this.attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
        new ArrayList<>(ids), TenantContext.getCurrentTenant());
  }

  private List<AttackPattern> getAttackPatternsByInternalIds(Set<String> ids) {
    if (ids.isEmpty()) {
      return Collections.emptyList();
    }
    return fromIterable(this.attackPatternRepository.findAllById(new ArrayList<>(ids)));
  }

  /**
   * Get the attack pattern IDs from the external IDs.
   *
   * @param externalAttackPatternIds Set of external attack pattern IDs to be converted to internal
   *     IDs.
   * @return List of attack pattern IDs corresponding to the external IDs.
   */
  private List<String> getAttackPatternInternalIdsFromExternalIds(
      Set<String> externalAttackPatternIds) {
    return this.getAttackPatternsByExternalIds(externalAttackPatternIds).stream()
        .map(AttackPattern::getId)
        .toList();
  }

  public List<AttackPattern> getAttackPatternsByExternalIdsThrowIfMissing(
      Set<String> externalAttackPatternIds) {
    List<AttackPattern> attackPatterns =
        this.getAttackPatternsByExternalIds(externalAttackPatternIds);
    List<String> missingIds =
        externalAttackPatternIds.stream()
            .filter(
                id ->
                    !attackPatterns.stream()
                        .map(ap -> ap.getExternalId().toLowerCase())
                        .toList()
                        .contains(id.toLowerCase()))
            .toList();
    if (!missingIds.isEmpty()) {
      throw new ElementNotFoundException(
          String.format("Missing attack patterns: %s", String.join(", ", missingIds)));
    }
    return attackPatterns;
  }

  public List<AttackPattern> findAllByInternalIdsThrowIfMissing(Set<String> ids) {
    List<AttackPattern> attackPatterns = this.getAttackPatternsByInternalIds(ids);
    List<String> missingIds =
        ids.stream()
            .filter(id -> !attackPatterns.stream().map(AttackPattern::getId).toList().contains(id))
            .toList();
    if (!missingIds.isEmpty()) {
      throw new ElementNotFoundException(
          String.format("Missing attack patterns: %s", String.join(", ", missingIds)));
    }
    return attackPatterns;
  }

  /**
   * Validate the inputs for the TTP Extraction AI Webservice.
   *
   * @param files List of files to be analyzed, maximum 5 files.
   * @param text Text input to be analyzed.
   */
  private void validateInputs(List<MultipartFile> files, String text) {
    if (files.isEmpty() && (text == null || text.isBlank())) {
      throw new IllegalArgumentException("Either files or text must be provided");
    }
    if (files.size() > 5) {
      throw new IllegalArgumentException("Maximum of 5 files allowed");
    }
    for (MultipartFile file : files) {
      if (file.getSize() > AI_UPLOAD_MAX_BYTES_PER_FILE) {
        throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "File '"
                + file.getOriginalFilename()
                + "' exceeds the AI upload size limit of "
                + (AI_UPLOAD_MAX_BYTES_PER_FILE / (1024 * 1024))
                + " MB");
      }
    }
  }

  /**
   * Search for attack patterns using the TTP Extraction AI Webservice.
   *
   * @param files List of files to be analyzed, maximum 5 files.
   * @param text Text input to be analyzed.
   * @param agentSlug XTM One agent slug to use when XTM One is configured.
   * @return List of attack pattern IDs found in the analysis.
   */
  public List<String> searchAttackPatternWithTTPAIWebservice(
      List<MultipartFile> files, String text, String agentSlug) {
    validateInputs(files, text);
    // Telemetry: one TTP extraction, counted before the routing branch so the
    // metric is identical whichever backend (legacy webservice or XTM One) serves it.
    aiMetricCollector.recordTtpExtraction();
    try {
      String responseBody;
      if (xtmOneConfig.isConfigured()) {
        responseBody = callTTPExtractionViaXtmOne(files, text, agentSlug);
      } else {
        responseBody = callTTPExtractionAIWebservice(files, text);
      }
      Set<String> attackPatternExternalIds =
          extractExternalAttackPatternIdsFromResponse(responseBody);
      return getAttackPatternInternalIdsFromExternalIds(attackPatternExternalIds);

    } catch (IOException | RestClientException e) {
      // Catch both IOException (XTM One branch) and RestClientException (legacy webservice via
      // RestTemplate) so upstream details are logged but never leak into the 503 response body.
      log.warn("[AttackPattern] AI service call failed.", e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI service is unavailable", e);
    }
  }

  /** Backwards-compatible overload — uses the default TTP extractor agent. */
  public List<String> searchAttackPatternWithTTPAIWebservice(
      List<MultipartFile> files, String text) {
    return searchAttackPatternWithTTPAIWebservice(files, text, null);
  }

  /**
   * Call the TTP extraction agent via XTM One. Converts MultipartFile attachments to base64 inline
   * format expected by the copilot agent, sends the request through {@link XtmOneClient}, and
   * returns the agent's content (same JSON format as the legacy webservice). The {@code agentSlug}
   * supplied by the caller is validated against the {@code cti.ttp_harvester} intent catalog so
   * users can only invoke agents that were registered as TTP extractors.
   */
  private String callTTPExtractionViaXtmOne(
      List<MultipartFile> files, String text, String agentSlug) throws IOException {
    com.fasterxml.jackson.databind.node.ArrayNode filesNode = null;
    if (!files.isEmpty()) {
      filesNode = mapper.createArrayNode();
      for (MultipartFile file : files) {
        var fileNode = mapper.createObjectNode();
        fileNode.put("filename", file.getOriginalFilename());
        fileNode.put(
            "content_type",
            file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        fileNode.put("data", Base64.getEncoder().encodeToString(file.getBytes()));
        filesNode.add(fileNode);
      }
    }

    String content = (text != null && !text.isBlank()) ? text : "Extract TTPs from attached files";
    String result = xtmOneClient.callAgentSync(agentSlug, content, filesNode);
    if (result == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "XTM One AI service is unavailable or returned no result");
    }
    return result;
  }

  // -- STIX --

  /**
   * Resolves external AttackPattern references from a {@link SecurityCoverage} into internal {@link
   * AttackPattern} entities.
   *
   * @param stixRefs list of tuples linking an atatck pattern ext ID with a stix ID
   * @return list of resolved internal AttackPattern entities
   */
  public Map<String, AttackPattern> fetchInternalAttackPatternIds(
      Set<StixRefToExternalRef> stixRefs) {
    return getAttackPatternsByExternalIds(securityCoverageUtils.getExternalIds(stixRefs)).stream()
        .collect(Collectors.toMap(attack -> attack.getId(), Function.identity()));
  }

  public List<AttackPattern> getAttackPattern(List<String> idsAttackPattern) {
    return attackPatternRepository.findAllByIdIn(idsAttackPattern);
  }

  private AttackPattern createAttackPatternFromAttackPatternCreateInput(
      AttackPatternCreateInput input) {
    AttackPattern newAttackPattern = new AttackPattern();
    newAttackPattern.setName(input.getName());
    newAttackPattern.setStixId(input.getStixId());
    newAttackPattern.setDescription(input.getDescription());
    newAttackPattern.setExternalId(input.getExternalId());
    newAttackPattern.setPlatforms(input.getPlatforms());
    newAttackPattern.setPermissionsRequired(input.getPermissionsRequired());
    newAttackPattern.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return newAttackPattern;
  }

  /**
   * Finds an existing attack pattern by external ID, or creates a new one if none exists.
   *
   * <p>Unlike {@link #internalUpsertAttackPatterns}, this method does <b>not</b> update an existing
   * entity.
   *
   * @param input the attack pattern data used for lookup (by external ID) and creation
   * @return the existing or newly created attack pattern
   */
  public AttackPattern findOrCreate(AttackPatternCreateInput input) {
    String tenant = TenantContext.getCurrentTenant();
    Optional<AttackPattern> attackPattern =
        attackPatternRepository
            .findAllByExternalIdInIgnoreCaseAndTenantId(List.of(input.getExternalId()), tenant)
            .stream()
            .findFirst();
    return attackPattern.orElseGet(
        () -> attackPatternRepository.save(createAttackPatternFromAttackPatternCreateInput(input)));
  }

  public List<AttackPattern> internalUpsertAttackPatterns(
      List<AttackPatternCreateInput> attackPatterns, Boolean ignoreDependencies) {
    List<AttackPattern> upserted = new ArrayList<>();
    attackPatterns.forEach(
        attackPatternInput -> {
          String attackPatternExternalId = attackPatternInput.getExternalId();
          Optional<AttackPattern> optionalAttackPattern =
              attackPatternRepository.findByExternalId(attackPatternExternalId);
          List<KillChainPhase> killChainPhases =
              attackPatternInput.getKillChainPhasesIds() != null
                      && !attackPatternInput.getKillChainPhasesIds().isEmpty()
                  ? fromIterable(
                      killChainPhaseRepository.findAllById(
                          attackPatternInput.getKillChainPhasesIds()))
                  : new ArrayList<>();
          AttackPattern attackPatternParent =
              attackPatternInput.getParentId() != null
                  ? attackPatternRepository
                      .findByStixId(attackPatternInput.getParentId())
                      .orElseThrow(ElementNotFoundException::new)
                  : null;
          if (optionalAttackPattern.isEmpty()) {
            attackPatternInput.setExternalId(attackPatternExternalId);
            AttackPattern newAttackPattern =
                createAttackPatternFromAttackPatternCreateInput(attackPatternInput);
            newAttackPattern.setKillChainPhases(killChainPhases);
            newAttackPattern.setExternalId(attackPatternExternalId);
            upserted.add(newAttackPattern);
          } else {
            AttackPattern attackPattern = optionalAttackPattern.get();
            // In this case, the input may not contain kill chain phases or parent, we keep the
            // original
            if (ignoreDependencies) {
              if (killChainPhases.isEmpty() && !attackPattern.getKillChainPhases().isEmpty()) {
                killChainPhases = attackPattern.getKillChainPhases();
              }
              if (attackPatternParent == null && attackPattern.getParent() != null) {
                attackPatternParent = attackPattern.getParent();
              }
            }
            attackPattern.setStixId(attackPatternInput.getStixId());
            attackPattern.setKillChainPhases(killChainPhases);
            attackPattern.setName(attackPatternInput.getName());
            attackPattern.setDescription(attackPatternInput.getDescription());
            attackPattern.setPlatforms(attackPatternInput.getPlatforms());
            attackPattern.setPermissionsRequired(attackPatternInput.getPermissionsRequired());
            attackPattern.setParent(attackPatternParent);
            upserted.add(attackPattern);
          }
        });
    return fromIterable(this.attackPatternRepository.saveAll(upserted));
  }
}
