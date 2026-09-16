package io.openaev.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated outcome of an import. Currently carries the actions that could not be recreated on the
 * target instance (partial import), so the REST layer can surface them to the front (partial-import
 * toast). A single ZIP import may trigger several {@link Importer#importData} calls (main
 * Exercise/Scenario file plus one or more direct-import / archived {@code payload.json}): their
 * missing actions must be aggregated into a single consolidated result.
 *
 * @param missingActions the actions skipped during import (never {@code null})
 */
public record ImportResult(List<MissingImportedAction> missingActions) {
  public ImportResult {
    missingActions = missingActions == null ? new ArrayList<>() : missingActions;
  }

  public static ImportResult empty() {
    return new ImportResult(new ArrayList<>());
  }
}
