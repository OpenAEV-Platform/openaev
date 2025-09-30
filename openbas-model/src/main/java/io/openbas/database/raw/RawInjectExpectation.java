package io.openbas.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawInjectExpectation {

  String getInject_expectation_id();

  String getInject_expectation_name();

  String getInject_title();

  String getInject_expectation_description();

  String getInject_expectation_type();

  String getInject_expectation_results();

  Double getInject_expectation_score();

  Double getInject_expectation_expected_score();

  Long getInject_expiration_time();

  Boolean getInject_expectation_group();

  Instant getInject_expectation_created_at();

  Instant getInject_expectation_updated_at();

  String getExercise_id();

  String getInject_id();

  String getUser_id();

  String getTeam_id();

  String getAgent_id();

  String getAsset_id();

  String getAsset_group_id();

  Set<String> getAttack_pattern_ids();

  Set<String> getSecurity_platform_ids();

  String getScenario_id();

  Instant getTracking_sent_date();
}
