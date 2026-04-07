package io.openaev.api.helper.queue.executor;

import io.openaev.api.inject.form.InjectExecutionCallback;
import io.openaev.api.inject.service.BatchingInjectStatusService;
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
