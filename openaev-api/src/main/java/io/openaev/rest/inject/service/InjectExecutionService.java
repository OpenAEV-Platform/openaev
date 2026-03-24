package io.openaev.rest.inject.service;

import static io.openaev.utils.ExecutionTraceUtils.convertExecutionAction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.service.InjectExpectationService;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.Map;
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
  private final InjectExpectationService injectExpectationService;
  private final AgentRepository agentRepository;
  private final InjectStatusService injectStatusService;
  private final InjectService injectService;

  private final AgentExecutionProcessingHandler agentExecutionProcessingHandler;
  private final InjectorExecutionProcessingHandler injectorExecutionProcessingHandler;

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
      if (agent == null) {
        processInjectExecutionWithInjector(inject, input);
      } else {
        processInjectExecutionWithAgent(inject, agent, input);
      }
    } catch (ElementNotFoundException e) {
      handleInjectExecutionError(inject, e);
    }
  }

  public void processInjectExecutionWithAgent(
      Inject inject, Agent agent, InjectExecutionInput input) {
    processInjectExecution(inject, agent, input, agentExecutionProcessingHandler);
  }

  public void processInjectExecutionWithInjector(Inject inject, InjectExecutionInput input) {
    processInjectExecution(inject, null, input, injectorExecutionProcessingHandler);
  }

  /**
   * Processes the execution of an inject by resolving the appropriate handler based on the
   * execution source (injector or agent), extracting findings, matching expectations and updating
   * the inject status.
   *
   * @param inject the inject being executed
   * @param agent the agent executing to inject, or {@code null} if triggered by an injector
   * @param input the execution input containing action, status, and output data
   * @throws RuntimeException if the output structured cannot be parsed
   */
  private void processInjectExecution(
      Inject inject,
      @Nullable Agent agent,
      InjectExecutionInput input,
      AbstractExecutionProcessingHandler handler) {
    try {
      Map<String, Endpoint> valueTargetedAssetsMap = injectService.getValueTargetedAssetMap(inject);
      // Build the context encapsulating all execution data and conditions (success, action, source)
      ExecutionProcessingContext executionContext =
          new ExecutionProcessingContext(inject, agent, input, valueTargetedAssetsMap);
      // Delegate to the appropriate handler (injector or agent) to process output execution
      ObjectNode resolvedStructured = handler.processContext(executionContext).orElse(null);

      injectStatusService.updateInjectStatus(inject, agent, input, resolvedStructured);
      addEndDateInjectExpectationTimeSignatureIfNeeded(inject, agent, input);

    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to process inject execution for inject", e);
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
