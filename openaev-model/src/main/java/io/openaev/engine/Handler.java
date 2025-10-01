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
   * @return list data to index
   */
  List<T> fetch(Instant from);
}
