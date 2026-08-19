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
   * Keyset-paged variant of {@link #fetch(Instant, int)}: resumes strictly after the total order
   * {@code (from, fromId)} rather than after {@code from} alone, so a batch whose LIMIT falls
   * inside a group of rows sharing one timestamp cannot skip the remainder.
   *
   * <p>That guarantee only holds outside the grace window. Inside it, {@link
   * io.openaev.service.EsIndexingUtils#capToGraceWindow} rewinds the cursor and drops the id every
   * round, so a group larger than the batch size re-serves its first rows until its timestamp ages
   * past the window. Rows are re-upserted idempotently, never skipped.
   *
   * <p>The default implementation ignores {@code fromId} and delegates, which is exactly the
   * historical behaviour for the handlers that have not opted in.
   *
   * @param fromId the {@code base_id} of the last document of the previous batch, null on the first
   *     round and whenever the grace-window cap applies
   */
  default List<T> fetch(Instant from, String fromId, int limit) {
    return fetch(from, limit);
  }

  /**
   * Whether this handler honours {@code fromId} in {@link #fetch(Instant, String, int)} and returns
   * rows ordered by {@code (updated_at, base_id)}. Drives which cursor the indexing loop persists.
   */
  default boolean isKeysetPaged() {
    return false;
  }
}
