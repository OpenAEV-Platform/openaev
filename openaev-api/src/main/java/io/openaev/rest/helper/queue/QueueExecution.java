package io.openaev.rest.helper.queue;

import java.util.List;

public interface QueueExecution<T> {
  void perform(List<T> elements);
}
