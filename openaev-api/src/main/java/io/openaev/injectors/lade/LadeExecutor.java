package io.openaev.injectors.lade;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.database.model.ExecutionTrace.getNewInfoTrace;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Injector;
import io.openaev.injectors.lade.service.LadeService;
import io.openaev.model.ExecutionProcess;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component(LadeContract.TYPE)
@RequiredArgsConstructor
public class LadeExecutor extends Injector {

  private final LadeService ladeService;

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection) {
    Inject inject = injection.getInjection().getInject();
    String bundleIdentifier = ""; // contract.getContext().get("bundle_identifier");
    String ladeType = ""; // contract.getContext().get("lade_type");
    ObjectNode content = inject.getContent();
    try {
      String actionWorkflowId;
      final InjectorContract injectorContract =
          inject
              .getInjectorContract()
              .orElseThrow(
                  () -> new UnsupportedOperationException("Inject does not have a contract"));
      switch (ladeType) {
        case "action" ->
            actionWorkflowId =
                ladeService.executeAction(bundleIdentifier, injectorContract.getId(), content);
        case "scenario" ->
            actionWorkflowId =
                ladeService.executeScenario(bundleIdentifier, injectorContract.getId(), content);
        default -> throw new UnsupportedOperationException(ladeType + " not supported");
      }
      String message = "Lade " + ladeType + " sent with workflow (" + actionWorkflowId + ")";
      execution.addTrace(
          getNewInfoTrace(message, ExecutionTraceAction.EXECUTION, List.of(actionWorkflowId)));
      return new ExecutionProcess(true);
    } catch (Exception e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }
  }
}
