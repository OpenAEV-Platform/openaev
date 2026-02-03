package io.openaev.utils.fixtures;

import io.openaev.database.model.STEP_ACTION_CLASS;
import io.openaev.database.model.STEP_STATUS;
import io.openaev.database.model.Step;

public class StepFixture {

  public static Step getDefaultStepTemplate() {
    Step step = new Step();
    step.setStepAction(STEP_ACTION_CLASS.INJECT_EXECUTION);
    step.setOutput("{}");
    step.setOutput_parser("{}");
    step.setInput("{}");
    step.setData("{}");
    step.setLimitExecution(1);
    step.setConditionExecuted("true");
    step.setStatus(STEP_STATUS.TEMPLATE);
    return step;
  }

  public static Step getDefaultStepExecution(STEP_STATUS status) {
    Step step = new Step();
    step.setStepAction(STEP_ACTION_CLASS.INJECT_EXECUTION);
    step.setOutput("{}");
    step.setOutput_parser("{}");
    step.setInput("{}");
    step.setData("{}");
    step.setLimitExecution(1);
    step.setConditionExecuted("true");
    step.setStatus(status);
    return step;
  }
}
