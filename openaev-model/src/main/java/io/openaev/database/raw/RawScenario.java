package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawScenario {

  public String getScenario_id();

  public String getScenario_name();

  public String getScenario_recurrence();

  Instant getScenario_created_at();

  Instant getScenario_injects_updated_at();

  public String getScenario_subtitle();

  public Set<String> getScenario_tags();

  public Set<String> getScenario_assets();

  public Set<String> getScenario_asset_groups();

  public Set<String> getScenario_teams();

  public Set<String> getScenario_injects();

  public Set<String> getScenario_platforms();
}
