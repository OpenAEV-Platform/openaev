package io.openaev.importer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated outcome of an import. Currently carries the actions that could not be recreated on the
 * target instance (partial import), so the REST layer can surface them to the front (partial-import
 * toast). A single ZIP import may trigger several {@link Importer#importData} calls (main
 * Exercise/Scenario file plus one or more direct-import / archived {@code payload.json}): their
 * missing actions must be aggregated into a single consolidated result.
 *
 * @param missingActions the actions skipped during import (never {@code null})
 * @param scenarioId the scenario the import created or filled, if any; for the caller, not the API
 * @param packageInjectIds the ID each created inject has in the imported file, by created ID
 */
public record ImportResult(
    List<MissingImportedAction> missingActions,
    @JsonIgnore String scenarioId,
    @JsonIgnore Map<String, String> packageInjectIds) {
  public ImportResult {
    missingActions = missingActions == null ? new ArrayList<>() : missingActions;
    packageInjectIds = packageInjectIds == null ? Map.of() : packageInjectIds;
  }

  public ImportResult(List<MissingImportedAction> missingActions) {
    this(missingActions, null, null);
  }

  public static ImportResult empty() {
    return new ImportResult(new ArrayList<>());
  }

  public ImportResult and(ImportResult other) {
    List<MissingImportedAction> actions = new ArrayList<>(missingActions);
    actions.addAll(other.missingActions());
    Map<String, String> injectIds = new HashMap<>(packageInjectIds);
    injectIds.putAll(other.packageInjectIds());
    return new ImportResult(
        actions, scenarioId != null ? scenarioId : other.scenarioId(), injectIds);
  }
}
