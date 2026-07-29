package io.openaev.database.raw;

/**
 * Minimal projection for computing global expectation scores.
 *
 * <p>Contains only the fields required for score normalization and aggregation. Used by the
 * repository queries that feed the global score computation (by inject IDs or exercise IDs).
 */
public interface RawGlobalScoreExpectation {

  String getInject_id();

  String getExercise_id();

  String getInject_expectation_type();

  Double getInject_expectation_score();

  Double getInject_expectation_expected_score();
}
