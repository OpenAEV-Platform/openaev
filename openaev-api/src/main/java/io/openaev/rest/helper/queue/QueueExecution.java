package io.openaev.rest.helper.queue;

import java.util.List;

public interface QueueExecution<T> {
  /**
   * Function that process a list of elements and return the list of successfully processed elements
   *
   * @param elements the elements to process
   * @return the successfully processed elements
   */
  List<T> perform(List<T> elements);
}
