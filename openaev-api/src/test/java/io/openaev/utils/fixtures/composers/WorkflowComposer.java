package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowComposer extends ComposerBase<Workflow> {

  @Autowired private WorkflowRepository workflowRepository;

  public class Composer extends InnerComposerBase<Workflow> {

    private final Workflow workflow;

    public Composer(Workflow workflow) {
      this.workflow = workflow;
    }

    /** Adds a Step to the workflow and sets both sides of the relationship. */
    public Composer withStep(Step step) {
      step.setWorkflow(workflow);
      workflow.getSteps().add(step);
      return this;
    }

    /** Sets the simulation for the workflow. */
    public Composer withSimulation(Exercise simulation) {
      workflow.setSimulation(simulation);
      return this;
    }

    /** Sets the workflow template and updates the template's executed workflows list. */
    public Composer withWorkflowTemplate(Workflow template) {
      workflow.setWorkflowTemplate(template);
      template.getWorkflowsExecuted().add(workflow);
      return this;
    }

    @Override
    public Composer persist() {
      // Persist the workflow; cascading will handle Steps if CascadeType.ALL is set
      workflowRepository.save(workflow);
      return this;
    }

    @Override
    public Composer delete() {
      workflowRepository.delete(workflow);
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
