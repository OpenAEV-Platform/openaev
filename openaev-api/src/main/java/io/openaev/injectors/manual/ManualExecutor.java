package io.openaev.injectors.manual;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.manual.model.ManualContent;
import io.openaev.model.ExecutionProcess;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;

public class ManualExecutor extends Injector {

  private final InjectExpectationService injectExpectationService;

  public ManualExecutor(
      InjectorContext context, final InjectExpectationService injectExpectationService) {
    super(context);
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection)
      throws Exception {

    ManualContent content = injectExpectationService.contentConvert(injection, ManualContent.class);
    injectExpectationService.computeAndSaveExpectations(injection, content.getExpectations(), null);
    execution.addTrace(
        ExecutionTrace.getNewSuccessTrace(
            "Manual inject execution", ExecutionTraceAction.COMPLETE));
    return new ExecutionProcess(false);
  }
}
