package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.ChainingConfiguration;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ChainingConfigurationRepository;
import io.openaev.database.repository.WorkflowRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowComposer extends ComposerBase<Workflow> {

  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private ChainingConfigurationRepository chainingConfigurationRepository;

  public class Composer extends InnerComposerBase<Workflow> {

    private final Workflow workflow;
    private Optional<ExerciseComposer.Composer> simulationComposer = Optional.empty();
    private final List<StepComposer.Composer> stepComposers = new ArrayList<>();
    private final List<WorkflowComposer.Composer> workflowComposers = new ArrayList<>();
    private Optional<ChainingConfiguration> chainingConfiguration = Optional.empty();

    public Composer(Workflow workflow) {
      this.workflow = workflow;
    }

    /** Adds a Step to the workflow and sets both sides of the relationship. */
    public Composer withStep(StepComposer.Composer stepComposer) {
      this.stepComposers.add(stepComposer);
      Step step = stepComposer.get();
      step.setWorkflow(workflow);
      workflow.getSteps().add(step);
      return this;
    }

    /** Sets the simulation for the workflow. */
    public Composer withSimulation(ExerciseComposer.Composer simulationComposer) {
      this.simulationComposer = Optional.of(simulationComposer);
      this.workflow.setSimulation(simulationComposer.get());
      return this;
    }

    /** Sets the workflow template and updates the template's executed workflows list. */
    public Composer withWorkflowTemplate(WorkflowComposer.Composer templateComposer) {
      this.workflowComposers.add(templateComposer);
      Workflow template = templateComposer.get();
      workflow.setWorkflowTemplate(template);
      template.getWorkflowsExecuted().add(workflow);
      return this;
    }

    /** Attaches a provided chaining configuration to the workflow. */
    public Composer withChainingConfiguration(ChainingConfiguration configuration) {
      this.chainingConfiguration = Optional.of(configuration);
      configuration.setWorkflow(workflow);
      workflow.setChainingConfiguration(configuration);
      return this;
    }

    /** Creates and attaches a default chaining configuration to the workflow. */
    public Composer withDefaultChainingConfiguration() {
      ChainingConfiguration configuration = new ChainingConfiguration();
      configuration.setRateLimitEnabled(false);
      configuration.setTimeoutEnabled(false);
      configuration.setSafeModeEnabled(true);
      return withChainingConfiguration(configuration);
    }

    @Override
    public Composer persist() {
      simulationComposer.ifPresent(ExerciseComposer.Composer::persist);
      workflowRepository.save(workflow);
      chainingConfiguration.ifPresent(chainingConfigurationRepository::save);
      workflowComposers.forEach(WorkflowComposer.Composer::persist);
      return this;
    }

    @Override
    public Composer delete() {
      chainingConfiguration.ifPresent(chainingConfigurationRepository::delete);
      workflowRepository.delete(workflow);
      simulationComposer.ifPresent(ExerciseComposer.Composer::delete);
      return this;
    }

    @Override
    public Workflow get() {
      return workflow;
    }
  }

  /** Entry point for workflow composition. */
  public Composer forWorkflow(Workflow workflow) {
    generatedItems.add(workflow);
    return new Composer(workflow);
  }
}
