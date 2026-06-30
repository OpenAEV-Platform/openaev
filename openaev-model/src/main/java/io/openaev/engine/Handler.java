package io.openaev.engine;

import io.openaev.engine.model.EsBase;
import java.time.Instant;
import java.util.List;

public interface Handler<T extends EsBase> {

  /**
   * To update documents and their attributes in the fetch method thanks to a "findForIndexing"
   * Postgres query
   *
   * <p>Specificity: we need to fill every attribute with a value, even if it is empty or null, to
   * inform that there is no value anymore in the attribute (null for String, List.of() or Set.of()
   * for List or Set)
   *
   * @param from date used to determine which data to take (updated_at attribute from table). For
   *     each attribute added, it is important to check that the updated at for the document is
   *     relevant when you delete/update/add this attribute in this document
   * @param limit maximum number of records to fetch per batch
   * @return list data to index
   */
  List<T> fetch(Instant from, int limit);

  /**
   * Variant of {@link #fetch(Instant, int)} using a compound keyset cursor (timestamp + last entity
   * ID) to avoid missing items that share the same {@code updated_at} timestamp when the previous
   * batch was full.
   *
   * <p>The default implementation ignores {@code lastId} and falls back to the basic cursor,
   * providing backward-compatible behaviour for handlers that do not need compound pagination.
   * Override in handlers where the same-millisecond duplicate issue is observable (e.g.
   * InjectExpectationHandler).
   *
   * @param from lower-bound timestamp (exclusive unless lastId is provided)
   * @param lastId ID of the last successfully indexed entity at {@code from}; when non-null the
   *     query returns items at {@code from} with ID strictly greater than this value, plus all
   *     items strictly after {@code from}
   * @param limit maximum number of records to fetch per batch
   * @return list data to index
   */
  default List<T> fetch(Instant from, String lastId, int limit) {
    return fetch(from, limit);
  }
}
