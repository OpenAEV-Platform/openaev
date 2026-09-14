package io.openaev.injectors.openaev;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.utils.ExpectationUtils.OAEV_IMPLANT;

import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.Inject;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.model.ExecutionProcess;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.InjectExpectationService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenAEVImplantExecutor extends Injector {

  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  public OpenAEVImplantExecutor(
      InjectorContext context,
      InjectExpectationService injectExpectationService,
      InjectService injectService) {
    super(context);
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
  }

  @Override
  public ExecutionProcess process(Execution execution, ExecutableInject injection)
      throws Exception {
    Inject inject = this.injectService.inject(injection.getInjection().getInject().getId());

    List<AssetToExecute> assetToExecutes = this.injectService.resolveAllAssetsToExecute(inject);
    injection.cacheAssetsToExecute(assetToExecutes);

    if (assetToExecutes.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "Found 0 asset to execute the ability on (likely this inject does not have any target or the targeted asset is inactive and has been purged)",
              ExecutionTraceAction.COMPLETE));
    }

    // Compute and save expectations
    injectExpectationService.computeAndSaveExpectations(injection, OAEV_IMPLANT);

    return new ExecutionProcess(true);
  }
}
