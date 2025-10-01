package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawTeam {

  String getTeam_id();

  String getTeam_name();

  String getTeam_description();

  Instant getTeam_created_at();

  Instant getTeam_updated_at();

  String getTeam_organization();

  boolean getTeam_contextual();

  Set<String> getTeam_tags();

  Set<String> getTeam_users();

  Set<String> getTeam_exercises();

  Set<String> getTeam_scenarios();

  Set<String> getTeam_expectations();

  Set<String> getTeam_exercise_injects();

  Set<String> getTeam_communications();
}
