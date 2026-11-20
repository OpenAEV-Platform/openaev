package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import jakarta.validation.Valid;

/** The scope and limits of the workflow of the installed scenario. */
public record ChainedScenarioInstallInput(
    @JsonProperty("workflow_configuration") @Valid WorkflowConfigurationInput workflowConfiguration)
    implements CatalogScenarioInstallInput {}
