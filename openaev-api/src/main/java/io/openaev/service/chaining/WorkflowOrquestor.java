package io.openaev.service.chaining;

import io.openaev.database.model.Workflow;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowOrchestrator {
  private final WorkflowStateService stateService;
  private final StepService stepService;
  private final WorkflowService workflowService;

  /**
   * Unified entry point for any change in the workflow.
   * Can be called by 'startWorkflow' or 'onInjectComplete'.
   */
  @Transactional
  public void evolve(Workflow run, Map<String, List<String>> newFacts) {
    if (newFacts.isEmpty()) return;

    // 1. Sync data to the pool
    stateService.syncState(run, newFacts);

    // 2. Evaluate what happens next
    stepService.evaluate(run);
  }

  /**
   * Specifically for starting the workflow.
   */
  public void start(Workflow run) {
    Map<String, List<String>> scopeFacts = workflowService.extractScopeData(run);
    evolve(run, scopeFacts);
  }
}