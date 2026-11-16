package io.openaev.service.catalog.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.ScopeVariableInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.chaining.WorkflowService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A chained scenario runs its payloads in the template steps of its workflow, which takes the
 * chosen scope and limits.
 */
@Component
@RequiredArgsConstructor
public class ChainedScenarioInstall implements CatalogScenarioTypeInstall<WorkflowChoice> {

  private final WorkflowService workflowService;
  private final InjectorContractService injectorContractService;
  private final ObjectMapper mapper;

  @Override
  public CatalogScenarioType type() {
    return CatalogScenarioType.CHAINED;
  }

  @Override
  public WorkflowChoice noChoice() {
    return WorkflowChoice.NONE;
  }

  @Override
  public Stream<Payload> payloadsRun(Scenario scenario) throws Exception {
    return template(scenario.getId()).map(Workflow::getSteps).orElse(List.of()).stream()
        .map(this::contractId)
        .flatMap(Optional::stream)
        .map(
            contractId ->
                injectorContractService
                    .injectorContractInTenant(contractId, scenario.getTenant().getId())
                    .getPayload())
        .filter(payload -> payload != null);
  }

  // Safe mode and variables stay as the package sets them.
  @Override
  public void apply(Scenario scenario, WorkflowChoice choice) throws Exception {
    WorkflowConfigurationInput chosen = choice.configuration();
    if (chosen == null) {
      return;
    }
    Optional<Workflow> workflow = template(scenario.getId());
    if (workflow.isPresent()) {
      workflowService.updateWorkflowConfiguration(
          workflow.get().getId(), installConfiguration(workflow.get(), chosen));
    }
  }

  private Optional<Workflow> template(String scenarioId) throws Exception {
    return workflowService.findWorkflowTemplateByScenarioId(scenarioId);
  }

  // A step names its contract by ID, or holds the whole contract.
  private Optional<String> contractId(Step step) {
    try {
      JsonNode contract = mapper.readTree(step.getData()).path("inject_injector_contract");
      return Optional.ofNullable(
          contract.isObject()
              ? contract.path("injector_contract_id").asText(null)
              : contract.asText(null));
    } catch (IOException e) {
      throw new IllegalStateException("Unreadable step data", e);
    }
  }

  private static WorkflowConfigurationInput installConfiguration(
      Workflow workflow, WorkflowConfigurationInput chosen) {
    return WorkflowConfigurationInput.builder()
        .rateLimitEnabled(chosen.isRateLimitEnabled())
        .maxAttempts(Optional.ofNullable(chosen.getMaxAttempts()).orElse(workflow.getMaxAttempts()))
        .maxTemporalRateSeconds(
            Optional.ofNullable(chosen.getMaxTemporalRateSeconds())
                .orElse(workflow.getMaxTemporalRateSeconds()))
        .timeoutEnabled(chosen.isTimeoutEnabled())
        .timeoutSeconds(
            Optional.ofNullable(chosen.getTimeoutSeconds()).orElse(workflow.getTimeoutSeconds()))
        .safeModeEnabled(workflow.isSafeModeEnabled())
        .workflowScopeRules(chosen.getWorkflowScopeRules())
        .workflowScopeVariables(
            workflow.getWorkflowScopeVariables().stream()
                .map(
                    variable ->
                        ScopeVariableInput.builder()
                            .id(variable.getId())
                            .key(variable.getKey())
                            .type(variable.getType())
                            .value(variable.getValue())
                            .description(variable.getDescription())
                            .build())
                .toList())
        .build();
  }
}
