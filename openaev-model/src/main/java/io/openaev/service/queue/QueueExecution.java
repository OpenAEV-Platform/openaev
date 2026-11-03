package io.openaev.service.queue;

import java.util.List;

public interface QueueExecution<T> {
  /**
   * Function that process a list of elements and return the list of successfully processed elements
   *
   * <p>The returned list must hold the same instances as {@code elements}, not copies: the caller
   * matches them by instance to acknowledge each RabbitMQ delivery, so two equal elements stay two
   * distinct deliveries.
   *
   * @param elements the elements to process
   * @return the successfully processed elements, as instances taken from {@code elements}
   */
  List<T> perform(List<T> elements);
}
