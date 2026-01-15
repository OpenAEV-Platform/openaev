package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawScenario {

  public String getScenario_id();

  public String getScenario_name();

  public String getScenario_category();

  public Instant getScenario_created_at();

  public Instant getScenario_updated_at();

  public String getScenario_custom_dashboard();

  public String getScenario_description();

  public String getScenario_external_url();

  public boolean getScenario_lessons_anonymized();

  public String getScenario_mail_from();

  public String getScenario_main_focus();

  public String getScenario_message_footer();

  public String getScenario_message_header();

  public String getScenario_recurrence();

  public Instant getScenario_recurrence_start();

  public Instant getScenario_recurrence_end();

  public String getScenario_subtitle();

  public Set<String> getScenario_dependencies();

  public String getScenario_severity();

  public Set<String> getScenario_exercises();

  public String getScenario_kill_chain_phases();

  public Set<String> getScenario_platforms();

  public Set<String> getScenario_tags();

  public String getScenario_teams_users();

  public long getScenario_users_number();

  public long getScenario_all_users_number();
}
