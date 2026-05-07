package io.openaev.api.attack_path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.WorkflowRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log
public class AttackPathService {

  private final WorkflowRepository workflowRepository;
  private final InjectExpectationRepository injectExpectationRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public AttackPathOutput buildAttackPath(String exerciseId) {
    // 1. Load workflow RUN(s) for this exercise
    List<Workflow> workflowRuns =
        workflowRepository.findAllBySimulation_IdAndStatus(exerciseId, WorkflowStatus.RUN);

    if (workflowRuns.isEmpty()) {
      return new AttackPathOutput(List.of(), List.of(), emptyStats());
    }

    // 2. Load all expectations for the exercise (single batch query)
    List<InjectExpectation> expectations =
        injectExpectationRepository.findAllForExercise(exerciseId);

    // 3. Assemble graph from workflow steps + expectations
    List<AttackPathNodeOutput> nodes = new ArrayList<>();
    List<AttackPathEdgeOutput> edges = new ArrayList<>();
    Set<String> assetNodeIds = new HashSet<>();

    // Map inject_id → expectations for status resolution
    Map<String, List<InjectExpectation>> expectationsByInject =
        expectations.stream()
            .filter(e -> e.getInject() != null)
            .collect(Collectors.groupingBy(e -> e.getInject().getId()));

    for (Workflow workflow : workflowRuns) {
      List<Step> steps = workflow.getSteps();

      for (Step step : steps) {
        if (step.getStatus() == StepStatus.TEMPLATE) continue;

        // Parse step data for inject info
        String injectId = extractInjectId(step);
        String payloadName = extractPayloadName(step);

        // Resolve status from expectations
        List<InjectExpectation> stepExpectations =
            injectId != null ? expectationsByInject.getOrDefault(injectId, List.of()) : List.of();
        String status = resolveStatus(stepExpectations);

        // Build expectations output
        List<AttackPathExpectationOutput> expectationOutputs =
            stepExpectations.stream()
                .filter(
                    e ->
                        e.getType() == InjectExpectation.EXPECTATION_TYPE.PREVENTION
                            || e.getType() == InjectExpectation.EXPECTATION_TYPE.DETECTION)
                .map(this::toExpectationOutput)
                .toList();

        // Create ACTION node
        nodes.add(
            new AttackPathNodeOutput(
                step.getId(),
                "ACTION",
                payloadName != null ? payloadName : "Action",
                status,
                null,
                null,
                null,
                payloadName,
                step.getUpdatedAt(),
                expectationOutputs));

        // Create ASSET nodes + asset_link edges from expectations
        for (InjectExpectation exp : stepExpectations) {
          Asset asset = exp.getAsset();
          if (asset == null) continue;

          if (!assetNodeIds.contains(asset.getId())) {
            assetNodeIds.add(asset.getId());
            nodes.add(buildAssetNode(asset));
          }

          edges.add(
              new AttackPathEdgeOutput(
                  step.getId() + "->" + asset.getId(),
                  step.getId(),
                  asset.getId(),
                  "asset_link",
                  null));
        }

        // Create chain_flow edges from conditions (step dependencies)
        for (Condition condition : step.getConditions()) {
          Step sourceStep = condition.getStepFrom();
          if (sourceStep != null) {
            String label = buildEventLabel(condition);
            edges.add(
                new AttackPathEdgeOutput(
                    sourceStep.getId() + "->" + step.getId(),
                    sourceStep.getId(),
                    step.getId(),
                    "chain_flow",
                    label));
          }
        }
      }
    }

    // 4. Compute stats
    AttackPathStatsOutput stats = computeStats(nodes);

    return new AttackPathOutput(nodes, edges, stats);
  }

  private JsonNode parseStepData(Step step) {
    try {
      String data = step.getData();
      if (data != null && !data.isBlank()) {
        return objectMapper.readTree(data);
      }
    } catch (Exception e) {
      log.fine("Could not parse step_data for step " + step.getId());
    }
    return null;
  }

  private String extractInjectId(Step step) {
    JsonNode data = parseStepData(step);
    if (data != null && data.has("inject_id")) {
      return data.get("inject_id").asText();
    }
    return null;
  }

  private String extractPayloadName(Step step) {
    JsonNode data = parseStepData(step);
    if (data != null && data.has("inject_title")) {
      return data.get("inject_title").asText();
    }
    return null;
  }

  private String resolveStatus(List<InjectExpectation> expectations) {
    if (expectations.isEmpty()) return "pending";

    boolean hasPrevention = false;
    boolean hasDetection = false;
    boolean preventionSuccess = false;
    boolean detectionSuccess = false;
    boolean allResolved = true;

    for (InjectExpectation exp : expectations) {
      if (exp.getType() == InjectExpectation.EXPECTATION_TYPE.PREVENTION) {
        hasPrevention = true;
        if (exp.getResponse() == InjectExpectation.EXPECTATION_STATUS.SUCCESS) {
          preventionSuccess = true;
        } else if (exp.getResponse() == InjectExpectation.EXPECTATION_STATUS.PENDING) {
          allResolved = false;
        }
      } else if (exp.getType() == InjectExpectation.EXPECTATION_TYPE.DETECTION) {
        hasDetection = true;
        if (exp.getResponse() == InjectExpectation.EXPECTATION_STATUS.SUCCESS) {
          detectionSuccess = true;
        } else if (exp.getResponse() == InjectExpectation.EXPECTATION_STATUS.PENDING) {
          allResolved = false;
        }
      }
    }

    if (!allResolved) return "pending";
    if (hasPrevention && preventionSuccess) return "prevented";
    if (hasDetection && detectionSuccess) return "detected";
    return "undetected";
  }

  private AttackPathExpectationOutput toExpectationOutput(InjectExpectation exp) {
    return new AttackPathExpectationOutput(
        exp.getId(),
        exp.getType().name(),
        exp.getResponse() != null ? exp.getResponse().name() : "PENDING",
        exp.getScore() != null ? (int) Math.round(exp.getScore()) : null,
        exp.getExpectedScore() != null ? (int) Math.round(exp.getExpectedScore()) : null);
  }

  private AttackPathNodeOutput buildAssetNode(Asset asset) {
    String hostname = null;
    String ip = null;
    String platform = null;

    if (asset instanceof Endpoint endpoint) {
      hostname = endpoint.getHostname();
      ip =
          endpoint.getIps() != null && endpoint.getIps().length > 0
              ? endpoint.getIps()[0]
              : null;
      platform = endpoint.getPlatform() != null ? endpoint.getPlatform().name() : null;
    }

    return new AttackPathNodeOutput(
        asset.getId(),
        "ASSET",
        hostname != null ? hostname : asset.getName(),
        null,
        hostname,
        ip,
        platform,
        null,
        null,
        null);
  }

  private String buildEventLabel(Condition condition) {
    ConditionKeyType keyType = condition.getKeyType();
    String value = condition.getValue();

    if (keyType == null) return null;

    String label = formatKeyType(keyType);
    if (value != null && !value.isBlank()) {
      label += " · " + value;
    }
    return label;
  }

  private String formatKeyType(ConditionKeyType keyType) {
    return switch (keyType) {
      case CREDENTIALS -> "Credentials Found";
      case USERNAME -> "Username Found";
      case ADMIN_USERNAME -> "Admin Username Found";
      case IPV4 -> "IPv4 Discovered";
      case IPV6 -> "IPv6 Discovered";
      case PORT -> "Port Discovered";
      case PORTSCAN -> "Portscan Complete";
      case CVE -> "CVE Identified";
      case SHARE -> "Share Found";
      case COMPUTER -> "Computer Found";
      case GROUP -> "Group Found";
      case SID -> "SID Found";
      case VULNERABILITY -> "Vulnerability Found";
      case PASSWORD_POLICY -> "Password Policy Found";
      case DELEGATION -> "Delegation Found";
      default -> keyType.name();
    };
  }

  private AttackPathStatsOutput computeStats(List<AttackPathNodeOutput> nodes) {
    List<AttackPathNodeOutput> actionNodes =
        nodes.stream().filter(n -> "ACTION".equals(n.type())).toList();

    int prevented = 0;
    int detected = 0;
    int undetected = 0;
    int pending = 0;

    for (AttackPathNodeOutput node : actionNodes) {
      switch (node.status()) {
        case "prevented" -> prevented++;
        case "detected" -> detected++;
        case "undetected" -> undetected++;
        default -> pending++;
      }
    }

    int executed = prevented + detected + undetected;
    return new AttackPathStatsOutput(
        prevented, detected, undetected, pending, actionNodes.size(), executed);
  }

  private AttackPathStatsOutput emptyStats() {
    return new AttackPathStatsOutput(0, 0, 0, 0, 0, 0);
  }
}
