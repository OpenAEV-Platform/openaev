package io.openaev.service.attackpath;

import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.projection.AttackPathEdgeGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointFindingRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointTypeCountRow;
import io.openaev.database.model.attackpath.projection.AttackPathExecutionRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingExecutionRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingListRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.database.model.attackpath.projection.AttackPathInjectorMetaRow;
import io.openaev.database.model.attackpath.projection.AttackPathSimSummaryRow;
import io.openaev.database.model.attackpath.projection.AttackPathTypeCountRow;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.StepConditionRow;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.payload.form.DetectionRemediationOutput;
import io.openaev.service.attackpath.dto.AttackPathAttackPatternDTO;
import io.openaev.service.attackpath.dto.AttackPathCounters;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathEdges;
import io.openaev.service.attackpath.dto.AttackPathEndpointRelationsDTO;
import io.openaev.service.attackpath.dto.AttackPathExecutionDetailDTO;
import io.openaev.service.attackpath.dto.AttackPathExecutionFindingItemDTO;
import io.openaev.service.attackpath.dto.AttackPathExpandDTO;
import io.openaev.service.attackpath.dto.AttackPathFindingItemDTO;
import io.openaev.service.attackpath.dto.AttackPathFindingPageDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.utils.mapper.PayloadMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilds a simulation's attack-path graph (issue 6647). The graph comes from two flat, indexed
 * reads (Read A: executions; Read B: findings joined to their producing execution) plus one
 * in-memory pass that turns the rows into {@code {nodes, edges, counters}} with the deterministic
 * IDs from {@link AttackPathIds}. Full mode adds one more batched read for the kill-chain fields
 * (the executions' step-template conditions, resolved once per distinct step template), skipped
 * when no execution carries a step template. No recursion, and the number of SQL statements is
 * constant (three in full mode, two otherwise), independent of the graph size. Each read is walked
 * exactly once (counters are accumulated inside the findings pass).
 *
 * <p>The execution is carried on the source-to-target edge (its {@code executionIds}), not as a
 * standalone map node (design O2), while the left feed still lists every execution.
 */
@Service
@RequiredArgsConstructor
public class AttackPathGraphService {

  private static final String SOURCE_INJECTOR = "INJECTOR";
  private static final String PREVENTED = ExpectationType.PREVENTION.successLabel;
  private static final String DETECTED = ExpectationType.DETECTION.successLabel;

  private static final String TYPE_INJECTOR = "INJECTOR";
  private static final String TYPE_ASSET = "ASSET";
  private static final String TYPE_EXECUTION = "EXECUTION";
  private static final String TYPE_FINDING_TYPE = "FINDING_TYPE";
  private static final String TYPE_FINDING = "FINDING";

  private static final String EDGE_EXECUTIONS = "EDGE_EXECUTIONS";
  private static final String EDGE_ENDPOINT_FINDINGS_TYPE = "EDGE_ENDPOINT_FINDINGS_TYPE";
  private static final String EDGE_FINDINGS_TYPE_FINDING = "EDGE_FINDINGS_TYPE_FINDING";

  private static final String GREEN = "GREEN";
  private static final String ORANGE = "ORANGE";
  private static final String RED = "RED";

  private static final String CATEGORY_CREDENTIALS = "credentials";
  private static final String CREDENTIAL_MASK = "••••";

  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathFindingRepository findingRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final PayloadRepository payloadRepository;
  private final StepRepository stepRepository;
  private final PayloadMapper payloadMapper;
  private final AttackPathKillChainResolver killChainResolver;
  private final ConditionRepository conditionRepository;
  private final AssetRepository assetRepository;

  /**
   * Above this many executions a simulation is served collapsed by default. Tied to the front
   * render ceiling, not the backend latency: a full graph of more nodes than this is not
   * renderable.
   */
  @Value("${openaev.attackpath.collapse-threshold:20000}")
  private long collapseThreshold;

  /**
   * Graph for a simulation, choosing the mode: {@code full} or {@code collapsed} forces it,
   * otherwise a large simulation (more executions than the collapse threshold) is served collapsed.
   * A cheap indexed {@code COUNT} decides; it costs a fraction of the collapsed rebuild.
   */
  @Transactional(readOnly = true)
  public AttackPathDTO buildGraph(String simulationId, String requestedMode) {
    // Both branches call the private bodies, never the public transactional twins: an intra-class
    // call bypasses the Spring proxy, so the inner annotation would be silently inert.
    return resolveCollapsed(simulationId, requestedMode)
        ? collapsedGraph(simulationId)
        : fullGraph(simulationId);
  }

  private boolean resolveCollapsed(String simulationId, String requestedMode) {
    if ("collapsed".equals(requestedMode)) {
      return true;
    }
    if ("full".equals(requestedMode)) {
      return false;
    }
    return executionRepository.countExecutions(simulationId) > collapseThreshold;
  }

  /**
   * Summaries of the simulations that have attack-path data in the caller's tenant, for the picker.
   * When {@code scenarioId} is given (scenario context, #6647 B0), the list is restricted to that
   * scenario's simulations; otherwise every simulation with attack-path data in the tenant.
   */
  @Transactional(readOnly = true)
  public List<AttackPathSimSummaryRow> listSimulations(String scenarioId) {
    return (scenarioId == null || scenarioId.isBlank())
        ? executionRepository.findSimulationSummaries()
        : executionRepository.findSimulationSummariesByScenario(scenarioId);
  }

  /**
   * A page of a widget category's findings for the drawer (issue 5048). Reads the page of findings
   * of the category's types (restricted to those a producing execution links to, the same invariant
   * as the graph reads), then attaches each finding's producing-execution ids and its endpoint's
   * map node id for cross-focus, and masks the value for the credentials category (a credential
   * value never leaves the server in the clear). An unknown category yields an empty page.
   */
  @Transactional(readOnly = true)
  public AttackPathFindingPageDTO listFindings(
      String simulationId, String category, Pageable pageable) {
    Set<String> types = categoryTypes(category);
    if (types.isEmpty()) {
      return new AttackPathFindingPageDTO(List.of(), 0);
    }
    Page<AttackPathFindingListRow> page =
        findingRepository.findPageByTypes(simulationId, types, pageable);
    List<AttackPathFindingListRow> rows = page.getContent();
    Map<String, List<String>> executionIdsByFinding = executionIdsByFinding(rows);
    boolean maskValue = CATEGORY_CREDENTIALS.equalsIgnoreCase(category);
    List<AttackPathFindingItemDTO> items =
        rows.stream()
            .map(
                r ->
                    new AttackPathFindingItemDTO(
                        r.type(),
                        maskValue ? maskCredential(r.value()) : r.value(),
                        r.endpointKey(),
                        AttackPathIds.endpointNode(r.endpointKey()),
                        executionIdsByFinding.getOrDefault(r.id(), List.of())))
            .toList();
    return new AttackPathFindingPageDTO(items, page.getTotalElements());
  }

  /**
   * One execution's Result &amp; Terminal detail for the drawer (issue 5048), from the frozen
   * snapshot (never the live inject). Reads the execution row (the only read that loads the heavy
   * {@code command}/{@code terminal_output}) and its produced findings, then masks the credential
   * secrets it surfaced in the command, the output, and the finding values. Returns {@code null}
   * when the execution is not in the caller's simulation (the controller maps that to 404).
   */
  @Transactional(readOnly = true)
  public AttackPathExecutionDetailDTO executionDetail(String simulationId, String executionId) {
    AttackPathExecution e =
        executionRepository.findByIdAndSimulationId(executionId, simulationId).orElse(null);
    if (e == null) {
      return null;
    }
    // Result tab: the findings this execution produced (credential values masked).
    List<AttackPathExecutionFindingItemDTO> findings = new ArrayList<>();
    for (AttackPathEndpointFindingRow f : findingRepository.findByExecutionId(executionId)) {
      boolean credential = CATEGORY_CREDENTIALS.equals(f.type());
      findings.add(
          new AttackPathExecutionFindingItemDTO(
              f.type(), credential ? maskCredential(f.value()) : f.value()));
    }
    // Mask, in the free-text command and output, the secrets of every credential discovered on this
    // endpoint: an execution's command references its endpoint's credentials, not only the ones it
    // links to, so endpoint-scoped masking never leaves a known secret in the clear.
    Set<String> secrets = new HashSet<>();
    for (AttackPathEndpointFindingRow f :
        findingRepository.findByEndpoint(simulationId, e.getTargetKey())) {
      if (CATEGORY_CREDENTIALS.equals(f.type())) {
        String secret = credentialSecret(f.value());
        if (secret != null && !secret.isEmpty()) {
          secrets.add(secret);
        }
      }
    }
    // ATT&CK techniques of the run's injector contract, for the drawer's technique chips. One
    // bounded lookup by the frozen contract external id (the accessor matches on id OR external id,
    // so the external id is passed for both).
    List<AttackPathAttackPatternDTO> attackPatterns = new ArrayList<>();
    if (e.getContractExternalId() != null) {
      injectorContractRepository
          .findByIdOrExternalId(e.getContractExternalId(), e.getContractExternalId())
          .ifPresent(
              contract ->
                  contract
                      .getAttackPatterns()
                      .forEach(
                          pattern ->
                              attackPatterns.add(
                                  new AttackPathAttackPatternDTO(
                                      pattern.getExternalId(), pattern.getName()))));
    }
    // Detection remediations of the payload that actually ran (the frozen payload id, not the
    // inject's current one). The mapper carries the EE gate: an inactive licence yields an empty
    // list, which is exactly what the drawer already renders.
    List<DetectionRemediationOutput> detectionRemediations =
        e.getPayloadId() == null
            ? List.of()
            : payloadMapper.toDetectionRemediationOutputs(
                payloadRepository
                    .findById(e.getPayloadId())
                    .map(p -> p.getDetectionRemediations())
                    .orElse(List.of()));
    // "Action details" opens the run's inject. The frozen row no longer stores the injectId (it is
    // a
    // live ref), so resolve it from the durable step the row is keyed by: the engine writes
    // inject_id
    // into step_data at run. Null (e.g. a not-yet-committed run) simply hides the front's button.
    String injectId =
        e.getStepId() == null
            ? null
            : stepRepository.findInjectIdByStepId(e.getStepId()).orElse(null);
    return new AttackPathExecutionDetailDTO(
        e.getPayloadName(),
        e.getStepId(),
        injectId,
        e.getPayloadId(),
        e.getAgentName(),
        e.getAgentPrivilege(),
        attackPatterns,
        detectionRemediations,
        e.getTargetKey(),
        e.getTargetHostname(),
        e.getTargetIp(),
        e.getTargetPlatform(),
        e.getPreventionStatus(),
        e.getDetectionStatus(),
        e.getExecutedAt() == null ? null : e.getExecutedAt().toString(),
        findings,
        maskSecrets(e.getCommand(), secrets),
        maskSecrets(e.getTerminalOutput(), secrets));
  }

  /** The secret half of a {@code username:password} credential value (never shown in the clear). */
  private static String credentialSecret(String value) {
    if (value == null) {
      return null;
    }
    int separator = value.indexOf(':');
    return separator >= 0 ? value.substring(separator + 1) : null;
  }

  /** Replaces each known credential secret with the fixed mask wherever it appears in free text. */
  private static String maskSecrets(String text, Set<String> secrets) {
    if (text == null || secrets.isEmpty()) {
      return text;
    }
    String masked = text;
    // Longest secret first, so a secret that is a substring of another does not corrupt the longer
    // one before it is masked (e.g. "pass" must not break "password").
    for (String secret :
        secrets.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
      masked = masked.replace(secret, CREDENTIAL_MASK);
    }
    return masked;
  }

  /**
   * The producing-execution ids per finding for a page of rows, from a single link read. The
   * finding ids come from a tenant-scoped page, so the read is already bounded to the tenant.
   */
  private Map<String, List<String>> executionIdsByFinding(List<AttackPathFindingListRow> rows) {
    if (rows.isEmpty()) {
      return Map.of();
    }
    List<String> findingIds = rows.stream().map(AttackPathFindingListRow::id).toList();
    Map<String, List<String>> byFinding = new LinkedHashMap<>();
    for (AttackPathFindingExecutionRow link : findingRepository.findExecutionLinks(findingIds)) {
      byFinding.computeIfAbsent(link.findingId(), k -> new ArrayList<>()).add(link.executionId());
    }
    return byFinding;
  }

  /**
   * The finding types each product widget aggregates (spec 5048 section 2:
   * Files/Credentials/Users/CVEs; Endpoints is a separate endpoint-group read, not a finding type).
   * {@code port} is a graph finding type but not a product widget, so it has no category here;
   * {@code file} has no seed finding type yet (an open question), so the files drawer is empty
   * until ingestion produces one. An unknown category yields no types (an empty page).
   */
  private static Set<String> categoryTypes(String category) {
    if (category == null) {
      return Set.of();
    }
    return switch (category.toLowerCase(Locale.ROOT)) {
      case CATEGORY_CREDENTIALS -> Set.of("credentials");
      case "users" -> Set.of("username", "admin_username");
      case "cves" -> Set.of("cve");
      case "files" -> Set.of("file");
      default -> Set.of();
    };
  }

  /**
   * Masks a credential for the drawer: for a {@code username:password} pair, keep the username and
   * mask only the secret; otherwise mask the whole value. The mask is fixed-length so it never
   * reveals the secret's length, and the clear secret never leaves the server.
   */
  private static String maskCredential(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int separator = value.indexOf(':');
    if (separator >= 0) {
      return value.substring(0, separator + 1) + CREDENTIAL_MASK;
    }
    return CREDENTIAL_MASK;
  }

  @Transactional(readOnly = true)
  public AttackPathDTO buildGraph(String simulationId) {
    return fullGraph(simulationId);
  }

  private AttackPathDTO fullGraph(String simulationId) {
    List<AttackPathExecutionRow> executions = executionRepository.findGraphRows(simulationId);
    List<AttackPathFindingRow> findings = findingRepository.findGraphRows(simulationId);
    return assemble(executions, findings);
  }

  /**
   * Expand one endpoint into its finding-type nodes and finding nodes, from a single indexed read.
   * {@code endpointKey} is the asset id or the raw value of a discovered endpoint.
   */
  @Transactional(readOnly = true)
  public AttackPathExpandDTO expandEndpoint(String simulationId, String endpointKey) {
    List<AttackPathEndpointFindingRow> findings =
        findingRepository.findByEndpoint(simulationId, endpointKey);
    String assetNodeId = AttackPathIds.endpointNode(endpointKey);
    Map<String, AttackPathNodeDTO> typeNodes = new LinkedHashMap<>();
    Map<String, AttackPathNodeDTO> findingNodes = new LinkedHashMap<>();
    for (AttackPathEndpointFindingRow f : findings) {
      String typeNodeId = AttackPathIds.findingTypeNode(f.type(), endpointKey);
      typeNodes.computeIfAbsent(typeNodeId, id -> findingTypeNode(id, f.type(), assetNodeId));
      String findingNodeId = AttackPathIds.findingNode(f.type(), f.value());
      findingNodes.computeIfAbsent(
          findingNodeId, id -> findingNode(id, f.type(), f.value(), typeNodeId, assetNodeId));
    }
    return new AttackPathExpandDTO(
        new ArrayList<>(typeNodes.values()), new ArrayList<>(findingNodes.values()));
  }

  /**
   * An endpoint's relations: the executions targeting it (as feed nodes) and the grouped edges into
   * it, from a single indexed read. {@code targetKey} is the asset id or the raw value.
   */
  @Transactional(readOnly = true)
  public AttackPathEndpointRelationsDTO endpointRelations(String simulationId, String targetKey) {
    List<AttackPathExecutionRow> executions =
        executionRepository.findByTarget(simulationId, targetKey);
    String targetNodeId = AttackPathIds.endpointNode(targetKey);
    Map<String, AttackPathEdges> edges = new LinkedHashMap<>();
    Map<String, AttackPathNodeDTO> feedByExecutionId = new LinkedHashMap<>();
    for (AttackPathExecutionRow e : executions) {
      String sourceNodeId = sourceNodeId(e);
      String edgeId = AttackPathIds.executionsEdge(sourceNodeId, targetNodeId);
      AttackPathEdges edge =
          edges.computeIfAbsent(edgeId, id -> executionEdge(id, sourceNodeId, targetNodeId));
      edge.setCount(edge.getCount() + 1);
      edge.getExecutionIds().add(e.id());
      feedByExecutionId.put(e.id(), executionFeedNode(e));
    }
    applyContractNames(executions, feedByExecutionId);
    return new AttackPathEndpointRelationsDTO(
        new ArrayList<>(feedByExecutionId.values()), new ArrayList<>(edges.values()));
  }

  private AttackPathDTO assemble(
      List<AttackPathExecutionRow> executions, List<AttackPathFindingRow> findings) {
    Map<String, AttackPathNodeDTO> nodes = new LinkedHashMap<>();
    Map<String, AttackPathEdges> edges = new LinkedHashMap<>();
    Map<String, AttackPathNodeDTO> feedByExecutionId = new LinkedHashMap<>();
    Map<String, List<AttackPathExecutionRow>> byTarget = new LinkedHashMap<>();
    // injector node id -> every contract that injector ran, so a node's techniques are their union.
    Map<String, Set<String>> contractsByInjectorNode = new LinkedHashMap<>();

    // Pass over executions: injector/source nodes, grouped execution edges, feed entries.
    for (AttackPathExecutionRow e : executions) {
      byTarget.computeIfAbsent(e.targetKey(), k -> new ArrayList<>()).add(e);

      String sourceNodeId = sourceNode(e, nodes);
      if (SOURCE_INJECTOR.equals(e.sourceKind()) && e.contractExternalId() != null) {
        contractsByInjectorNode
            .computeIfAbsent(sourceNodeId, k -> new LinkedHashSet<>())
            .add(e.contractExternalId());
      }
      String targetNodeId = AttackPathIds.endpointNode(e.targetKey());

      String edgeId = AttackPathIds.executionsEdge(sourceNodeId, targetNodeId);
      AttackPathEdges edge =
          edges.computeIfAbsent(edgeId, id -> executionEdge(id, sourceNodeId, targetNodeId));
      edge.setCount(edge.getCount() + 1);
      edge.getExecutionIds().add(e.id());

      feedByExecutionId.put(e.id(), executionFeedNode(e));
    }
    applyKillChain(executions, feedByExecutionId);
    applyContractNames(executions, feedByExecutionId);

    // Endpoint (ASSET) nodes, with attributes and colour from the executions targeting them.
    for (Map.Entry<String, List<AttackPathExecutionRow>> entry : byTarget.entrySet()) {
      String nodeId = AttackPathIds.endpointNode(entry.getKey());
      nodes.put(nodeId, assetNode(nodeId, entry.getKey(), entry.getValue()));
    }

    // Single pass over findings: finding-type nodes, finding nodes (deduped by type+value), finding
    // edges, the execution -> finding-node cross-reference, and the counters. No extra query,
    // no second walk of the findings.
    List<AttackPathNodeDTO> staticFindings = new ArrayList<>();
    Set<String> seenFindingNodes = new HashSet<>();
    Map<String, List<String>> findingNodeIdsByExecution = new LinkedHashMap<>();
    Set<String> credentialKeys = new HashSet<>();
    Set<String> userKeys = new HashSet<>();
    Set<String> cveKeys = new HashSet<>();
    Set<String> portKeys = new HashSet<>();
    for (AttackPathFindingRow f : findings) {
      String assetNodeId = AttackPathIds.endpointNode(f.endpointKey());
      String typeNodeId = AttackPathIds.findingTypeNode(f.type(), f.endpointKey());
      nodes.computeIfAbsent(typeNodeId, id -> findingTypeNode(id, f.type(), assetNodeId));

      edges.computeIfAbsent(
          AttackPathIds.endpointFindingTypeEdge(f.type(), f.endpointKey()),
          id -> plainEdge(id, assetNodeId, typeNodeId, EDGE_ENDPOINT_FINDINGS_TYPE));

      String findingNodeId = AttackPathIds.findingNode(f.type(), f.value());
      AttackPathNodeDTO findingNode =
          nodes.computeIfAbsent(
              findingNodeId, id -> findingNode(id, f.type(), f.value(), typeNodeId, assetNodeId));
      if (seenFindingNodes.add(findingNodeId)) {
        staticFindings.add(findingNode);
      }

      edges.computeIfAbsent(
          AttackPathIds.findingTypeFindingEdge(f.type(), f.endpointKey(), f.value()),
          id -> plainEdge(id, typeNodeId, findingNodeId, EDGE_FINDINGS_TYPE_FINDING));

      List<String> produced =
          findingNodeIdsByExecution.computeIfAbsent(f.executionId(), k -> new ArrayList<>());
      if (!produced.contains(findingNodeId)) {
        produced.add(findingNodeId);
      }

      // Counters, distinct by (type, value) so the same value across endpoints counts once
      // (plan §4 / D2). The type is a fixed prefix per category, so "|" cannot cause a collision.
      String counterKey = f.type() + "|" + f.value();
      switch (f.type()) {
        case "credentials" -> credentialKeys.add(counterKey);
        case "username", "admin_username" -> userKeys.add(counterKey);
        case "cve" -> cveKeys.add(counterKey);
        case "port" -> portKeys.add(counterKey);
        default -> {
          // other finding types do not feed a top-bar counter
        }
      }
    }

    // Wire the execution -> findings cross-reference onto the feed nodes.
    findingNodeIdsByExecution.forEach(
        (executionId, findingNodeIds) -> {
          AttackPathNodeDTO feedNode = feedByExecutionId.get(executionId);
          if (feedNode != null) {
            feedNode.setFindingsNodeIds(findingNodeIds);
          }
        });

    resolveInjectorAttackPatterns(nodes, contractsByInjectorNode);
    applyEndpointCriticality(nodes);

    AttackPathCounters counters =
        new AttackPathCounters(
            byTarget.size(),
            credentialKeys.size(),
            userKeys.size(),
            cveKeys.size(),
            portKeys.size());
    return new AttackPathDTO(
        staticFindings,
        new ArrayList<>(feedByExecutionId.values()),
        new ArrayList<>(nodes.values()),
        new ArrayList<>(edges.values()),
        counters,
        "full");
  }

  /**
   * The collapsed injector nodes get the same type and techniques as the full graph, but their
   * source rows are never materialized (the edges are grouped), so a small distinct read supplies
   * the per-injector metadata before the shared technique resolution runs.
   */
  private void enrichCollapsedInjectors(String simulationId, Map<String, AttackPathNodeDTO> nodes) {
    Map<String, Set<String>> contractsByInjectorNode = new LinkedHashMap<>();
    for (AttackPathInjectorMetaRow meta : executionRepository.findInjectorMetadata(simulationId)) {
      String nodeId = AttackPathIds.injectorNode(meta.sourceInjector());
      AttackPathNodeDTO injectorNode = nodes.get(nodeId);
      if (injectorNode == null) {
        continue;
      }
      if (injectorNode.getInjectorType() == null) {
        injectorNode.setInjectorType(meta.injectorType());
      }
      if (meta.contractExternalId() != null) {
        contractsByInjectorNode
            .computeIfAbsent(nodeId, k -> new LinkedHashSet<>())
            .add(meta.contractExternalId());
      }
    }
    resolveInjectorAttackPatterns(nodes, contractsByInjectorNode);
  }

  /**
   * Sets each injector node's ATT&CK techniques from its contracts, in ONE batched query for the
   * whole graph. A node's techniques are the union across every contract that injector ran, deduped
   * by technique id, since one injector can run several contracts in a simulation. No injector
   * contract in scope means no query at all, so a graph without injectors stays at its two reads.
   */
  private void resolveInjectorAttackPatterns(
      Map<String, AttackPathNodeDTO> nodes, Map<String, Set<String>> contractsByInjectorNode) {
    Set<String> externalIds = new HashSet<>();
    contractsByInjectorNode.values().forEach(externalIds::addAll);
    if (externalIds.isEmpty()) {
      return;
    }
    Map<String, List<AttackPathAttackPatternDTO>> patternsByContract = new HashMap<>();
    injectorContractRepository
        .findInjectorAttackPatternsByExternalIdIn(externalIds)
        .forEach(
            row ->
                patternsByContract
                    .computeIfAbsent(row.contractExternalId(), k -> new ArrayList<>())
                    .add(
                        new AttackPathAttackPatternDTO(
                            row.patternExternalId(), row.patternName())));
    contractsByInjectorNode.forEach(
        (nodeId, contractIds) -> {
          Map<String, AttackPathAttackPatternDTO> deduped = new LinkedHashMap<>();
          for (String contractId : contractIds) {
            patternsByContract
                .getOrDefault(contractId, List.of())
                .forEach(p -> deduped.putIfAbsent(p.externalId(), p));
          }
          if (!deduped.isEmpty()) {
            nodes.get(nodeId).setAttackPatterns(new ArrayList<>(deduped.values()));
          }
        });
  }

  /**
   * Collapsed rebuild for a large simulation (issue 6647, ADR-003): the same {@link AttackPathDTO}
   * with {@code mode = collapsed}, built entirely from DB aggregations, so the per-execution and
   * per-finding rows are never materialized. Nodes are the injectors and one node per endpoint
   * (carrying its status and a per-type finding-count summary); edges are grouped; the
   * per-execution and per-finding lists are empty, and the front loads detail on click via the
   * expand/relations endpoints.
   */
  @Transactional(readOnly = true)
  public AttackPathDTO buildCollapsedGraph(String simulationId) {
    return collapsedGraph(simulationId);
  }

  private AttackPathDTO collapsedGraph(String simulationId) {
    List<AttackPathEndpointGroupRow> endpoints =
        executionRepository.findEndpointGroups(simulationId);
    List<AttackPathEdgeGroupRow> edges = executionRepository.findEdgeGroups(simulationId);
    List<AttackPathTypeCountRow> typeCounts = findingRepository.findTypeCounts(simulationId);
    List<AttackPathEndpointTypeCountRow> endpointTypeCounts =
        findingRepository.findEndpointTypeCounts(simulationId);

    Map<String, Map<String, Long>> findingCountsByEndpoint = new LinkedHashMap<>();
    for (AttackPathEndpointTypeCountRow row : endpointTypeCounts) {
      findingCountsByEndpoint
          .computeIfAbsent(row.endpointKey(), k -> new LinkedHashMap<>())
          .put(row.type(), row.distinctValues());
    }

    Map<String, AttackPathNodeDTO> nodes = new LinkedHashMap<>();
    for (AttackPathEndpointGroupRow e : endpoints) {
      String nodeId = AttackPathIds.endpointNode(e.targetKey());
      AttackPathNodeDTO node = new AttackPathNodeDTO();
      node.setId(nodeId);
      node.setType(TYPE_ASSET);
      node.setRef(e.targetKey());
      node.setHostname(e.targetHostname());
      node.setIp(e.targetIp());
      node.setPlatform(e.targetPlatform());
      node.setLabel(
          e.targetHostname() != null && !e.targetHostname().isBlank()
              ? e.targetHostname()
              : e.targetKey());
      node.setStatus(collapsedColour(e.redCount(), e.orangeCount()));
      node.setFindingCounts(findingCountsByEndpoint.get(e.targetKey()));
      nodes.put(nodeId, node);
    }

    List<AttackPathEdges> collapsedEdges = new ArrayList<>();
    for (AttackPathEdgeGroupRow g : edges) {
      String sourceNodeId = collapsedSourceNodeId(g);
      nodes.computeIfAbsent(sourceNodeId, id -> collapsedSourceNode(g, id));
      String targetNodeId = AttackPathIds.endpointNode(g.targetKey());
      AttackPathEdges edge =
          plainEdge(
              AttackPathIds.executionsEdge(sourceNodeId, targetNodeId),
              sourceNodeId,
              targetNodeId,
              EDGE_EXECUTIONS);
      edge.setCount((int) g.count());
      collapsedEdges.add(edge);
    }

    enrichCollapsedInjectors(simulationId, nodes);
    applyEndpointCriticality(nodes);

    return new AttackPathDTO(
        List.of(),
        List.of(),
        new ArrayList<>(nodes.values()),
        collapsedEdges,
        collapsedCounters(endpoints.size(), typeCounts),
        "collapsed");
  }

  private AttackPathCounters collapsedCounters(
      int endpoints, List<AttackPathTypeCountRow> typeCounts) {
    long credentials = 0;
    long users = 0;
    long cves = 0;
    long ports = 0;
    for (AttackPathTypeCountRow t : typeCounts) {
      switch (t.type()) {
        case "credentials" -> credentials += t.distinctValues();
        case "username", "admin_username" -> users += t.distinctValues();
        case "cve" -> cves += t.distinctValues();
        case "port" -> ports += t.distinctValues();
        default -> {
          // other finding types do not feed a top-bar counter
        }
      }
    }
    return new AttackPathCounters(endpoints, credentials, users, cves, ports);
  }

  /** Worst-case severity of an endpoint's executions from the aggregated red/orange counts. */
  private String collapsedColour(long redCount, long orangeCount) {
    if (redCount > 0) {
      return RED;
    }
    if (orangeCount > 0) {
      return ORANGE;
    }
    return GREEN;
  }

  private String collapsedSourceNodeId(AttackPathEdgeGroupRow g) {
    return SOURCE_INJECTOR.equals(g.sourceKind())
        ? AttackPathIds.injectorNode(g.sourceInjector())
        : AttackPathIds.endpointNode(g.sourceAssetId());
  }

  private AttackPathNodeDTO collapsedSourceNode(AttackPathEdgeGroupRow g, String id) {
    if (SOURCE_INJECTOR.equals(g.sourceKind())) {
      return node(id, TYPE_INJECTOR, g.sourceInjector());
    }
    // Agent/asset source: its frozen attributes, so a source-only endpoint is not a bare id. The
    // endpoint pass runs first and already put a richer target node for a source that is also a
    // target, so this computeIfAbsent only ever creates a node for a source-only endpoint.
    AttackPathNodeDTO sourceEndpoint = new AttackPathNodeDTO();
    sourceEndpoint.setId(id);
    sourceEndpoint.setType(TYPE_ASSET);
    sourceEndpoint.setRef(g.sourceAssetId());
    sourceEndpoint.setHostname(g.sourceHostname());
    sourceEndpoint.setIp(g.sourceIp());
    sourceEndpoint.setPlatform(g.sourcePlatform());
    sourceEndpoint.setLabel(g.sourceHostname() != null ? g.sourceHostname() : g.sourceAssetId());
    return sourceEndpoint;
  }

  private String sourceNodeId(AttackPathExecutionRow e) {
    return SOURCE_INJECTOR.equals(e.sourceKind())
        ? AttackPathIds.injectorNode(e.sourceInjector())
        : AttackPathIds.endpointNode(e.sourceAssetId());
  }

  private String sourceNode(AttackPathExecutionRow e, Map<String, AttackPathNodeDTO> nodes) {
    String id = sourceNodeId(e);
    if (SOURCE_INJECTOR.equals(e.sourceKind())) {
      nodes.computeIfAbsent(
          id,
          key -> {
            AttackPathNodeDTO injectorNode = node(key, TYPE_INJECTOR, e.sourceInjector());
            // The injector's real type, frozen on the row; attack patterns are resolved once
            // after the pass, batched, in resolveInjectorAttackPatterns.
            injectorNode.setInjectorType(e.injectorType());
            return injectorNode;
          });
    } else {
      // Agent/asset source: the source endpoint, from its frozen source attributes. If it is also a
      // target (a pivot chain), the ASSET pass overwrites it with the richer target snapshot; if it
      // is only ever a source, these frozen values are all it has, so the node is not a bare id.
      nodes.computeIfAbsent(
          id,
          key -> {
            AttackPathNodeDTO sourceEndpoint = new AttackPathNodeDTO();
            sourceEndpoint.setId(key);
            sourceEndpoint.setType(TYPE_ASSET);
            sourceEndpoint.setRef(e.sourceAssetId());
            sourceEndpoint.setHostname(e.sourceHostname());
            sourceEndpoint.setIp(e.sourceIp());
            sourceEndpoint.setPlatform(e.sourcePlatform());
            sourceEndpoint.setLabel(
                e.sourceHostname() != null ? e.sourceHostname() : e.sourceAssetId());
            return sourceEndpoint;
          });
    }
    return id;
  }

  private AttackPathNodeDTO assetNode(
      String id, String targetKey, List<AttackPathExecutionRow> executions) {
    AttackPathExecutionRow representative =
        executions.stream()
            .max(Comparator.comparing(AttackPathExecutionRow::executedAt, nullsFirst()))
            .orElse(executions.get(0));
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(id);
    node.setType(TYPE_ASSET);
    node.setRef(targetKey);
    node.setHostname(representative.targetHostname());
    node.setIp(representative.targetIp());
    node.setPlatform(representative.targetPlatform());
    node.setLabel(
        representative.targetHostname() != null && !representative.targetHostname().isBlank()
            ? representative.targetHostname()
            : targetKey);
    node.setAgents(
        executions.stream()
            .map(AttackPathExecutionRow::agentName)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
    node.setStatus(endpointColour(executions));
    return node;
  }

  /**
   * The three-state severity of one execution, combining prevention and detection: GREEN if it was
   * prevented (blocked), else ORANGE if it was detected but not prevented (seen but got through),
   * else RED (neither detected nor prevented, the worst).
   */
  private String severity(String preventionStatus, String detectionStatus) {
    if (PREVENTED.equals(preventionStatus)) {
      return GREEN;
    }
    if (DETECTED.equals(detectionStatus)) {
      return ORANGE;
    }
    return RED;
  }

  private static int severityRank(String colour) {
    if (RED.equals(colour)) {
      return 2;
    }
    return ORANGE.equals(colour) ? 1 : 0;
  }

  /**
   * An endpoint takes the worst-case severity of the executions targeting it (RED > ORANGE >
   * GREEN).
   */
  private String endpointColour(List<AttackPathExecutionRow> executions) {
    String worst = GREEN;
    for (AttackPathExecutionRow e : executions) {
      String s = severity(e.preventionStatus(), e.detectionStatus());
      if (severityRank(s) > severityRank(worst)) {
        worst = s;
      }
      if (RED.equals(worst)) {
        return RED;
      }
    }
    return worst;
  }

  /**
   * Sets the kill-chain fields ({@code dependsOn} + {@code consumedFindingKeys}) on each execution
   * feed node, resolved once per distinct step template from its conditions in a single batched
   * read. Full mode only; a step with no conditions or an execution with no step template is left
   * untouched (the fields stay null and are omitted from the JSON).
   */
  private void applyKillChain(
      List<AttackPathExecutionRow> executions, Map<String, AttackPathNodeDTO> feedByExecutionId) {
    Set<String> stepTemplateIds =
        executions.stream()
            .map(AttackPathExecutionRow::stepTemplateId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (stepTemplateIds.isEmpty()) {
      return;
    }
    Map<String, AttackPathKillChainResolver.KillChainMeta> metaByStep = new HashMap<>();
    conditionRepository.findAllLinkedToStepIdIn(stepTemplateIds).stream()
        .collect(
            Collectors.groupingBy(
                StepConditionRow::stepTemplateId,
                Collectors.mapping(StepConditionRow::condition, Collectors.toList())))
        .forEach(
            (stepId, conditions) -> metaByStep.put(stepId, killChainResolver.resolve(conditions)));

    for (AttackPathExecutionRow e : executions) {
      AttackPathKillChainResolver.KillChainMeta meta =
          e.stepTemplateId() == null ? null : metaByStep.get(e.stepTemplateId());
      if (meta == null) {
        continue;
      }
      AttackPathNodeDTO node = feedByExecutionId.get(e.id());
      if (node == null) {
        continue;
      }
      if (!meta.dependsOn().isEmpty()) {
        node.setDependsOn(meta.dependsOn());
      }
      if (!meta.consumedFindingKeys().isEmpty()) {
        node.setConsumedFindingKeys(meta.consumedFindingKeys());
      }
    }
  }

  /**
   * Resolves each execution's injector-contract name (e.g. "NMAP SYN Scan") from its contract
   * external id and sets it on the execution feed node, so the front can name WHAT was launched on
   * the inject→endpoint edge. Batched over the DISTINCT external ids (a run uses a handful of
   * contracts, not one per execution), so this is a few reads regardless of the execution count.
   * No-op when no execution carries a contract.
   */
  private void applyContractNames(
      List<AttackPathExecutionRow> executions, Map<String, AttackPathNodeDTO> feedByExecutionId) {
    Set<String> externalIds =
        executions.stream()
            .map(AttackPathExecutionRow::contractExternalId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (externalIds.isEmpty()) {
      return;
    }
    Map<String, String> nameByExternalId = new HashMap<>();
    for (String externalId : externalIds) {
      injectorContractRepository
          .findByIdOrExternalId(externalId, externalId)
          .ifPresent(
              contract -> {
                String name = contractLabel(contract.getLabels());
                if (name != null) {
                  nameByExternalId.put(externalId, name);
                }
              });
    }
    if (nameByExternalId.isEmpty()) {
      return;
    }
    for (AttackPathExecutionRow e : executions) {
      String externalId = e.contractExternalId();
      if (externalId == null) {
        continue;
      }
      AttackPathNodeDTO node = feedByExecutionId.get(e.id());
      if (node != null) {
        node.setContractName(nameByExternalId.get(externalId));
      }
    }
  }

  /**
   * Sets each endpoint (ASSET) node's business criticality from its backing asset, in one batched
   * read over the asset ids (endpoint node refs). Discovered endpoints (raw values, not asset ids)
   * simply match nothing and stay null. Feeds the front's chokepoint score (findings weighted by
   * criticality).
   */
  private void applyEndpointCriticality(Map<String, AttackPathNodeDTO> nodes) {
    Map<String, AttackPathNodeDTO> assetNodesByRef = new HashMap<>();
    for (AttackPathNodeDTO node : nodes.values()) {
      if (TYPE_ASSET.equals(node.getType()) && node.getRef() != null) {
        assetNodesByRef.put(node.getRef(), node);
      }
    }
    if (assetNodesByRef.isEmpty()) {
      return;
    }
    for (Object[] row : assetRepository.findCriticalityByIds(assetNodesByRef.keySet())) {
      String assetId = (String) row[0];
      Object criticality = row[1];
      String name = (String) row[2];
      AttackPathNodeDTO node = assetNodesByRef.get(assetId);
      if (node == null) {
        continue;
      }
      if (criticality != null) {
        node.setCriticality(criticality.toString());
      }
      // Prefer the asset's friendly name over the raw id fallback (an ASSET endpoint with no
      // hostname
      // otherwise reads as its uuid on the map and in the chokepoint list).
      if (name != null && !name.isBlank()) {
        node.setLabel(name);
      }
    }
  }

  /** The contract's display name from its locale labels: English if present, else any label. */
  private static String contractLabel(Map<String, String> labels) {
    if (labels == null || labels.isEmpty()) {
      return null;
    }
    String en = labels.get("en");
    return en != null ? en : labels.values().iterator().next();
  }

  private AttackPathNodeDTO executionFeedNode(AttackPathExecutionRow e) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(AttackPathIds.executionNode(e.id(), e.targetKey(), e.agentId()));
    node.setType(TYPE_EXECUTION);
    // The raw execution id, so a findings drawer item can match and highlight its producing
    // executions in the feed (cross-focus); the map node id above is not the raw id.
    node.setRef(e.id());
    node.setLabel(e.payloadName());
    node.setStatus(severity(e.preventionStatus(), e.detectionStatus()));
    node.setPayloadName(e.payloadName());
    node.setExecutedAt(e.executedAt() == null ? null : e.executedAt().toString());
    node.setAgentName(e.agentName());
    node.setPrivilege(e.agentPrivilege());
    node.setStepTemplateId(e.stepTemplateId());
    return node;
  }

  private AttackPathNodeDTO findingTypeNode(String id, String type, String assetNodeId) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(id);
    node.setType(TYPE_FINDING_TYPE);
    node.setLabel(type);
    node.setTypeFindings(type);
    node.setAssetNodeId(assetNodeId);
    return node;
  }

  private AttackPathNodeDTO findingNode(
      String id, String type, String value, String typeNodeId, String assetNodeId) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(id);
    node.setType(TYPE_FINDING);
    node.setLabel(value);
    node.setValue(value);
    node.setTypeFindings(type);
    node.setFindingsTypeNodeId(typeNodeId);
    node.setAssetNodeId(assetNodeId);
    return node;
  }

  private AttackPathNodeDTO node(String id, String type, String label) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(id);
    node.setType(type);
    node.setLabel(label);
    return node;
  }

  private AttackPathEdges executionEdge(String id, String sourceNodeId, String targetNodeId) {
    AttackPathEdges edge = plainEdge(id, sourceNodeId, targetNodeId, EDGE_EXECUTIONS);
    edge.setCount(0);
    return edge;
  }

  private AttackPathEdges plainEdge(String id, String source, String target, String type) {
    AttackPathEdges edge = new AttackPathEdges();
    edge.setEdgeId(id);
    edge.setEdgeSourceId(source);
    edge.setEdgeTargetId(target);
    edge.setType(type);
    edge.setCount(1);
    return edge;
  }

  private static Comparator<Instant> nullsFirst() {
    return Comparator.nullsFirst(Comparator.naturalOrder());
  }
}
