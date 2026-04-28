package io.openaev.service.chaining;

import com.google.gson.JsonElement;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ChainingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionOrchestrator {

  private final WorkflowStateService workflowStateService;
  private final StepService stepService;

  private final WorkflowRepository workflowRepository;
  private final StepDelayQueueRepository stepDelayQueueRepository;

  /**
   * Synchronizes workflow state with provided data, reevaluates runnable steps, then persists the
   * updated workflow run.
   */
  @Transactional(rollbackFor = Exception.class)
  public void syncStateAndEvaluateWorkflowProgress(
      JsonElement dataToSync, Map<String, ContractOutputType> fieldTypeMap, Workflow workflowRun)
      throws ChainingException {

    if (dataToSync == null || dataToSync.isJsonNull()) {
      log.warn("Received empty data for sync. Skipping.");
      return;
    }

    workflowStateService.syncState(dataToSync, fieldTypeMap, workflowRun);
    evaluateWorkflowProgress(workflowRun);

    workflowRepository.save(workflowRun);
  }

  public Workflow evaluateWorkflowProgress(Workflow workflowRun) throws ChainingException {
    String workflowTemplateId = workflowRun.getWorkflowTemplate().getId();

    // Get all step template
    List<Step> stepsTemplate = stepService.findAllStepTemplateByWorkflow(workflowTemplateId);

    if (stepsTemplate.isEmpty()) {
      log.info(
          "No step template for workflow template {}. End running {}",
          workflowTemplateId,
          workflowRun.getId());
      workflowRun.setStatus(WorkflowStatus.END);
      return workflowRun;
    }

    // Step template with valid conditions
    List<Step> stepWithValidCondition = new ArrayList<>();

    for (Step step : stepsTemplate) {
      Optional<Step> stepReadyOpt = stepService.ready(step, workflowRun, null);
      stepReadyOpt.ifPresent(stepWithValidCondition::add);
    }

    // If none step TEMPLATE with valid conditions && no step template delayed update workflow with
    // status END
    if (stepWithValidCondition.isEmpty()
        && stepDelayQueueRepository.findAllByWorkflowRun(workflowRun).isEmpty()) {
      workflowRun.setStatus(WorkflowStatus.END);
    }

    return workflowRun;
  }
}
