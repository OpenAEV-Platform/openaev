package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StepComposer extends ComposerBase<Step> {

  @Autowired private StepRepository stepRepository;

  public class Composer extends InnerComposerBase<Step> {

    private final Step step;
    private Optional<WorkflowComposer.Composer> workflowComposer = Optional.empty();

    public Composer(Step step) {
      this.step = step;
    }

    /** Sets the workflow for the step and adds the step to the workflow's steps list. */
    public Composer withWorkflow(WorkflowComposer.Composer workflowComposer) {
      this.workflowComposer = Optional.of(workflowComposer);
      Workflow workflow = workflowComposer.get();
      step.setWorkflow(workflow);
      workflow.getSteps().add(step);
      return this;
    }

    /** Sets the step template and updates the template's executed steps list. */
    public Composer withStepTemplate(StepComposer.Composer templateComposer) {
      Step template = templateComposer.get();
      step.setStepTemplate(template);
      template.getStepsExecuted().add(step);
      return this;
    }

    @Override
    public StepComposer.Composer persist() {
      workflowComposer.ifPresent(WorkflowComposer.Composer::persist);
      stepRepository.save(step);
      return this;
    }

    @Override
    public StepComposer.Composer delete() {
      stepRepository.delete(step);
      workflowComposer.ifPresent(WorkflowComposer.Composer::delete);
      return this;
    }

    @Override
    public Step get() {
      return step;
    }
  }

  /** Entry point for step composition. */
  public StepComposer.Composer forStep(Step step) {
    generatedItems.add(step);
    return new Composer(step);
  }
}
