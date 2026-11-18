package io.openaev.service.catalog.scenario;

import io.openaev.api.chaining.dto.WorkflowConfigurationInput;

/** The scope and limits an admin chooses for the workflow of a chained scenario. */
public record WorkflowChoice(WorkflowConfigurationInput configuration)
    implements ScenarioInstallChoice {

  /** Nothing chosen: the workflow keeps what its package sets. */
  public static final WorkflowChoice NONE = new WorkflowChoice(null);

  @Override
  public CatalogScenarioType type() {
    return CatalogScenarioType.CHAINED;
  }
}
