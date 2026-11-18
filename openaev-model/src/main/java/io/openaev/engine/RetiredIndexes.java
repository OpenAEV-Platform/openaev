package io.openaev.engine;

import java.util.List;
import java.util.Map;

/**
 * Indexes of models that no longer exist.
 *
 * <p>The drivers create and clean up indexes by iterating over the registered models, so an index
 * whose model was removed or renamed would simply be left behind - and keep answering queries,
 * because every search runs against the {@code <prefix>_*} index pattern. Its documents would be
 * counted next to the ones of the model that replaced it. Listing the retired name here makes the
 * drivers drop it (index, rollover alias and template) at startup; the delete is a no-op once it is
 * gone, so the entry can stay until the next major version cleanup.
 */
public final class RetiredIndexes {

  /** {@code endpoint} became {@code asset} when the index was widened to the whole inventory. */
  public static final List<String> NAMES = List.of("endpoint");

  /**
   * The model that replaced a retired one, so a caller still using the old name in a widget filter
   * is told which value to use instead of reading an empty result.
   */
  public static final Map<String, String> SUCCESSORS = Map.of("endpoint", "asset");

  private RetiredIndexes() {}
}
