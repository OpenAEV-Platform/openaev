package io.openaev.service.chaining;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ChainingException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionOrchestrator {

  private static final Gson GSON = new Gson();

  private final WorkflowStateService workflowStateService;
  private final WorkflowRepository workflowRepository;
  private final ObjectProvider<StepService> stepServiceProvider;

  /**
   * Synchronizes workflow state with provided data, reevaluates runnable steps, then persists the
   * updated workflow run.
   */
  @Transactional(rollbackFor = Exception.class)
  public void syncAndEvaluate(
      JsonElement dataToSync, Map<String, ContractOutputType> fieldTypeMap, Workflow workflowRun)
      throws ChainingException {

    if (dataToSync == null || dataToSync.isJsonNull()) {
      log.warn("Received empty data for sync. Skipping.");
      return;
    }

    workflowStateService.syncState(dataToSync, fieldTypeMap, workflowRun);
    stepServiceProvider.getObject().evaluate(workflowRun);
    workflowRepository.save(workflowRun);
  }

  /** Starts workflow evaluation by seeding state from whitelist scope rules. */
  @Transactional(rollbackFor = Exception.class)
  public void startWorkflow(Workflow workflowRun) throws ChainingException {
    Map<String, ContractOutputType> fieldTypeMap =
        java.util.Arrays.stream(ContractOutputType.values())
            .collect(Collectors.toMap(ContractOutputType::name, type -> type));
    Map<String, List<String>> scopeData = extractScopeData(workflowRun);
    syncAndEvaluate(GSON.toJsonTree(scopeData), fieldTypeMap, workflowRun);
  }

  private Map<String, List<String>> extractScopeData(Workflow workflowRun) {
    if (workflowRun.getWhitelist() == null) {
      return Collections.emptyMap();
    }
    return workflowRun.getWhitelist().stream()
        .collect(
            Collectors.groupingBy(
                rule -> rule.getValueType().getContractOutputType(),
                Collectors.mapping(WorkflowScopeRule::getRuleValue, Collectors.toList())));
  }
}

