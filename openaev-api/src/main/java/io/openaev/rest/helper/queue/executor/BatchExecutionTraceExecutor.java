package io.openaev.rest.helper.queue.executor;

import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.service.BatchingInjectStatusService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchExecutionTraceExecutor {

  private final BatchingInjectStatusService batchingInjectStatusService;

  public List<InjectExecutionCallback> handleInjectExecutionCallbackList(
      List<InjectExecutionCallback> injectExecutionCallbacks) {
    return batchingInjectStatusService.handleInjectExecutionCallback(injectExecutionCallbacks);
  }
}
