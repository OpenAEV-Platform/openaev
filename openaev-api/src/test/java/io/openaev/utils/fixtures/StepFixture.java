package io.openaev.utils.fixtures;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;

public class StepFixture {

  public static Step getDefaultStepTemplate() {
    Step step = new Step();
    step.setStepAction(StepActionClass.INJECT_EXECUTION);
    step.setOutput("{}");
    step.setOutputParser("{}");
    step.setInput("{}");
    step.setData("{}");
    step.setLimitExecution(1);
    step.setConditionExecuted("true");
    step.setStatus(StepStatus.TEMPLATE);
    return step;
  }

  public static Step getDefaultStepExecution(StepStatus status) {
    Step step = new Step();
    step.setStepAction(StepActionClass.INJECT_EXECUTION);
    step.setOutput("{}");
    step.setOutputParser("{}");
    step.setInput("{}");
    step.setData("{}");
    step.setLimitExecution(1);
    step.setConditionExecuted("true");
    step.setStatus(status);
    return step;
  }
}
