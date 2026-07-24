package io.openaev.database.raw;

import java.util.List;

/**
 * Spring Data projection for aggregated inject metadata of an exercise.
 *
 * <p>Returns the distinct platforms, total communications count, and distinct kill-chain phase IDs
 * without loading individual inject rows — suitable for exercises with thousands of injects.
 */
public interface RawExerciseInjectSummary {

  List<String> getPlatforms();

  long getCommunications_number();

  List<String> getKill_chain_phase_ids();
}
