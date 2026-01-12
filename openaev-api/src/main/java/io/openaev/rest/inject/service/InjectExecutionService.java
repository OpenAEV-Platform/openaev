package io.openaev.rest.inject.service;

import static io.openaev.expectation.ExpectationType.VULNERABILITY;
import static io.openaev.utils.ExecutionTraceUtils.convertExecutionAction;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForVulnerabilityManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.service.InjectExpectationService;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class InjectExecutionService {

  private final InjectRepository injectRepository;
  private final InjectExpectationRepository injectExpectationRepository;
  private final InjectExpectationService injectExpectationService;
  private final AgentRepository agentRepository;
  private final InjectStatusService injectStatusService;
  private final FindingService findingService;
  private final StructuredOutputUtils structuredOutputUtils;

  @Resource protected ObjectMapper mapper;

  @Transactional
  public void handleInjectExecutionCallback(
      String injectId, String agentId, InjectExecutionInput input) {
    Inject inject = null;

    try {
      inject = loadInjectOrThrow(injectId);
      // issue/3550: added this condition to ensure we only update statuses if the inject is in a
      // coherent state.
      // This prevents issues where the PENDING status took more time to persist than it took for
      // the agent to send the complete action.
      // FIXME: At the moment, this whole function is called by our implant and injectors. These
      // implant are
      // launched with the async value to true, which force the implant to go from EXECUTING to
      // PENDING, before going to EXECUTED.
      // So if in the future, this function is called to update a synchronous inject, we will need
      // to find a way to get the async boolean somehow and add it to this condition.
      if (InjectExecutionAction.complete.equals(input.getAction())
          && (inject.getStatus().isEmpty()
              || !ExecutionStatus.PENDING.equals(inject.getStatus().get().getName()))) {
        // If we receive a status update with a terminal state status, we must first check that the
        // current status is in the PENDING state
        log.warn(
            String.format(
                "Received a complete action for inject %s with status %s, but current status is not PENDING",
                injectId, inject.getStatus().map(is -> is.getName().toString()).orElse("unknown")));
        throw new DataIntegrityViolationException(
            "Cannot complete inject that is not in PENDING state");
      }
      Agent agent = loadAgentIfPresent(agentId);

      Set<OutputParser> outputParsers = structuredOutputUtils.extractOutputParsers(inject);

      processInjectExecution(inject, agent, input, outputParsers);
    } catch (ElementNotFoundException e) {
      handleInjectExecutionError(inject, e);
    }
  }

  /** Processes the execution of an inject by updating its status and extracting findings. */
  public void processInjectExecution(
      Inject inject,
      @Nullable Agent agent,
      InjectExecutionInput input,
      Set<OutputParser> outputParsers) {
    ObjectNode structured = null;
    try {
      if (input.getOutputStructured() != null) {
        structured = mapper.readValue(input.getOutputStructured(), ObjectNode.class);
      }
      // Only compute if the action is actual execution
      if (ExecutionTraceAction.EXECUTION.equals(convertExecutionAction(input.getAction()))) {
        // validate vulnerability expectations
        structured =
            structuredOutputUtils
                .computeStructuredOutputFromOutputParsers(outputParsers, input.getMessage())
                .orElse(null);
        if (ExecutionTraceStatus.SUCCESS.toString().equals(input.getStatus())) {
          checkCveExpectation(outputParsers, structured, inject, agent);
        }
      }

      injectStatusService.updateInjectStatus(agent, inject, input, structured);
      addEndDateInjectExpectationTimeSignatureIfNeeded(inject, agent, input);

      if (agent != null) {
        // Extract findings from structured outputs generated by the output parsers specified in the
        // payload, typically derived from the raw output of the implant execution.
        findingService.extractFindingsFromOutputParsers(inject, agent, outputParsers, structured);
      } else {
        // Structured output directly provided (e.g., from injectors)
        findingService.extractFindingsFromInjectorContract(inject, structured);
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Adds an end date signature to inject expectations if the action is COMPLETE.
   *
   * @param inject the inject for which to add the end date signature
   * @param agent the agent for which to add the end date signature
   * @param input the input containing the action and duration
   */
  @VisibleForTesting
  public void addEndDateInjectExpectationTimeSignatureIfNeeded(
      Inject inject, Agent agent, InjectExecutionInput input) {
    if (agent != null
        && ExecutionTraceAction.COMPLETE.equals(convertExecutionAction(input.getAction()))) {
      InjectStatus injectStatus = inject.getStatus().orElseThrow();
      Instant endDate =
          injectStatusService.getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
              injectStatus, agent.getId(), input.getDuration());
      injectExpectationService.addEndDateSignatureToInjectExpectationsByAgent(
          inject.getId(), agent.getId(), endDate);
    }
  }

  /**
   * Checks output parsers of an agent and updates the scores of vulnerability expectations
   * accordingly
   *
   * @param outputParsers
   * @param structuredOutput
   * @param inject
   * @param agent
   */
  public void checkCveExpectation(
      Set<OutputParser> outputParsers, ObjectNode structuredOutput, Inject inject, Agent agent) {

    List<InjectExpectation> injectExpectations =
        inject.getExpectations().stream()
            .filter(exp -> exp.getAgent() != null && exp.getAgent().getId().equals(agent.getId()))
            .filter(exp -> InjectExpectation.EXPECTATION_TYPE.VULNERABILITY == exp.getType())
            .toList();

    if (injectExpectations.isEmpty()) {
      return;
    }

    InjectExpectationResult injectExpectationResult = buildForVulnerabilityManager();
    boolean vulnerable;

    // Determine vulnerability
    if (outputParsers.isEmpty()) {
      vulnerable = false;
    } else {
      boolean hasCveType =
          outputParsers.stream()
              .flatMap(parser -> parser.getContractOutputElements().stream())
              .anyMatch(element -> ContractOutputType.CVE == element.getType());

      if (!hasCveType) {
        vulnerable = false;
      } else {
        boolean hasCveData = false;

        if (structuredOutput != null) {
          hasCveData =
              outputParsers.stream()
                  .flatMap(parser -> parser.getContractOutputElements().stream())
                  .filter(element -> ContractOutputType.CVE == element.getType())
                  .map(element -> structuredOutput.get(element.getKey()))
                  .anyMatch(jsonNode -> jsonNode != null && !jsonNode.isEmpty());
        }

        vulnerable = hasCveData;
      }
    }

    // Set expectations based on result
    if (vulnerable) {
      setResultExpectationVulnerable(
          injectExpectations, injectExpectationResult, VULNERABILITY.failureLabel);
    } else { // Not vulnerable
      setResultExpectationVulnerable(
          injectExpectations, injectExpectationResult, VULNERABILITY.successLabel);
    }

    // Validate and save once
    validateResultForAsset(injectExpectations, injectExpectationResult);
    injectExpectationRepository.saveAll(injectExpectations);
  }

  public void validateResultForAsset(
      List<InjectExpectation> injectExpectations, InjectExpectationResult injectExpectationResult) {
    injectExpectations.forEach(
        injectExpectation -> {
          injectExpectationService.updateInjectExpectation(
              injectExpectation.getId(),
              InjectExpectationUpdateInput.builder()
                  .collectorId(injectExpectationResult.getSourceId())
                  .result(injectExpectationResult.getResult())
                  .isSuccess(injectExpectationResult.getScore() != 0.0)
                  .build());
        });
  }

  private Agent loadAgentIfPresent(String agentId) {
    return (agentId == null)
        ? null
        : agentRepository
            .findById(agentId)
            .orElseThrow(() -> new ElementNotFoundException("Agent not found: " + agentId));
  }

  private Inject loadInjectOrThrow(String injectId) {
    return injectRepository
        .findById(injectId)
        .orElseThrow(() -> new ElementNotFoundException("Inject not found: " + injectId));
  }

  public void handleInjectExecutionError(Inject inject, Exception e) {
    log.error(e.getMessage(), e);
    if (inject != null) {
      inject
          .getStatus()
          .ifPresent(
              status -> {
                ExecutionTrace trace =
                    new ExecutionTrace(
                        status,
                        ExecutionTraceStatus.ERROR,
                        null,
                        e.getMessage(),
                        ExecutionTraceAction.COMPLETE,
                        null,
                        Instant.now());
                status.addTrace(trace);
              });
      injectRepository.save(inject);
    }
  }
}
