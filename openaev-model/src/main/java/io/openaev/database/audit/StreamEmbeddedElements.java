package io.openaev.database.audit;

import io.openaev.context.MarkingCtx;
import io.openaev.database.model.Base;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Objects of a table derived from assets that an entity's stream payload embeds whole, not as ids
 * (an inject's expectations and execution traces), each with the markings of the asset it belongs
 * to ({@link Base#getStreamMarkings()}), copied when the event is built. A reader receives only the
 * elements their clearance allows: the REST API reads them through the SQL filtering, the stream
 * sends what the publisher loaded.
 *
 * @param path the JSON path of the array in the payload, dot-separated (e.g. {@code
 *     inject_status.status_traces})
 * @param idProperty the JSON property identifying an element of the array
 * @param markingsById the markings of each element's asset, empty for an element on no asset (a
 *     team expectation, a trace with no agent)
 */
public record StreamEmbeddedElements(
    String path, String idProperty, Map<String, Set<String>> markingsById) {

  public StreamEmbeddedElements {
    markingsById = Map.copyOf(markingsById);
  }

  public static StreamEmbeddedElements of(
      String path, String idProperty, Collection<? extends Base> elements) {
    return new StreamEmbeddedElements(
        path,
        idProperty,
        elements.stream()
            .filter(Objects::nonNull)
            .collect(
                Collectors.toMap(
                    Base::getId,
                    element -> BaseEvent.markingSet(element.getStreamMarkings()),
                    (first, second) -> first)));
  }

  /** The elements the clearance allows. */
  public Set<String> visibleIds(MarkingCtx clearance) {
    return markingsById.entrySet().stream()
        .filter(element -> clearance.allows(element.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }
}
