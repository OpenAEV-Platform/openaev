package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.WorkflowRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowComposer extends ComposerBase<Workflow> {

  @Autowired private WorkflowRepository workflowRepository;

  public class Composer extends InnerComposerBase<Workflow> {

    private final Workflow workflow;
    private Optional<ExerciseComposer.Composer> simulationComposer = Optional.empty();
    private Optional<ScenarioComposer.Composer> scenarioComposer = Optional.empty();
    private final List<StepComposer.Composer> stepComposers = new ArrayList<>();
    private final List<WorkflowComposer.Composer> workflowComposers = new ArrayList<>();

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

    /**
     * Applies default inline configuration (rate-limit and timeout disabled, safe-mode enabled)
     * directly onto the workflow row.
     */
    public Composer withDefaultWorkflowConfiguration() {
      workflow.setRateLimitEnabled(false);
      workflow.setTimeoutEnabled(false);
      workflow.setSafeModeEnabled(true);
      return this;
    }

    @Override
    public Composer persist() {
      simulationComposer.ifPresent(ExerciseComposer.Composer::persist);
      scenarioComposer.ifPresent(ScenarioComposer.Composer::persist);
      workflowRepository.save(workflow);
      workflowComposers.forEach(WorkflowComposer.Composer::persist);
      return this;
    }

    @Override
    public Composer delete() {
      workflowRepository.delete(workflow);
      simulationComposer.ifPresent(ExerciseComposer.Composer::delete);
      return this;
    }

    @Override
    public Workflow get() {
      return workflow;
    }

    public Composer withScenario(ScenarioComposer.Composer scenarioComposer) {
      this.scenarioComposer = Optional.of(scenarioComposer);
      this.workflow.setScenario(scenarioComposer.get());

      return this;
    }
  }

  /** Entry point for workflow composition. */
  public Composer forWorkflow(Workflow workflow) {
    generatedItems.add(workflow);
    return new Composer(workflow);
  }
}
