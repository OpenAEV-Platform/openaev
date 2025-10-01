package io.openaev.injectors.manual;

import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Injector;
import io.openaev.injectors.manual.model.ManualContent;
import io.openaev.model.ExecutionProcess;
import io.openaev.model.Expectation;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component(ManualContract.TYPE)
@RequiredArgsConstructor
public class ManualExecutor extends Injector {

  private final InjectExpectationService injectExpectationService;

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection)
      throws Exception {

    ManualContent content = contentConvert(injection, ManualContent.class);

    List<Expectation> expectations =
        content.getExpectations().stream()
            .flatMap(
                (entry) ->
                    switch (entry.getType()) {
                      case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                      default -> Stream.of();
                    })
            .toList();

    injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);
    execution.addTrace(
        ExecutionTrace.getNewSuccessTrace(
            "Manual inject execution", ExecutionTraceAction.COMPLETE));
    return new ExecutionProcess(false);
  }
}
