package io.openaev.rest.helper.queue.executor;

import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.service.BatchingInjectStatusService;
import io.openaev.rest.inject.service.InjectExecutionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchExecutionTraceExecutor {

  private final BatchingInjectStatusService batchingInjectStatusService;
  private final InjectExecutionService injectExecutionService;

  public void handleInjectExecutionCallbackList(
      List<InjectExecutionCallback> injectExecutionCallbacks) {
    batchingInjectStatusService.handleInjectExecutionCallback(injectExecutionCallbacks);
  }
}
