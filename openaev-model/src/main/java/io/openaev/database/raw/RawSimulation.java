package io.openaev.database.raw;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface RawSimulation {

  String getExercise_id();

  String getExercise_name();

  String getExercise_description();

  String getExercise_status();

  String getExercise_subtitle();

  String getExercise_category();

  String getExercise_main_focus();

  String getExercise_severity();

  Instant getExercise_start_date();

  Instant getExercise_end_date();

  String getExercise_message_header();

  String getExercise_message_footer();

  String getExercise_mail_from();

  boolean getExercise_lessons_anonymized();

  Instant getExercise_created_at();

  Instant getExercise_updated_at();

  Instant getExercise_injects_updated_at();

  String getScenario_id();

  String getExercise_custom_dashboard();

  Set<String> getExercise_reply_to();

  Set<String> getExercise_tags();

  Set<String> getExercise_assets();

  Set<String> getExercise_asset_groups();

  Set<String> getExercise_teams();

  Set<String> getExercise_users();

  Set<String> getExercise_platforms();

  Set<String> getLessons_answers();

  Set<String> getLogs();

  List<String> getInject_ids();
}
