package io.openaev.database.raw;

import io.openaev.database.model.Scenario.SEVERITY;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class RawPaginationScenario {

  private String scenario_id;
  private String scenario_name;
  private String scenario_description;
  private SEVERITY scenario_severity;
  private String scenario_category;
  private String scenario_recurrence;
  private Instant scenario_updated_at;
  private Set<String> scenario_tags;
  private Set<String> scenario_platforms;
  private Set<String> scenario_domains;
  private String scenario_workflow_id;
  private String scenario_author_name;
  private String scenario_version;
  private Instant scenario_last_run_at;

  public RawPaginationScenario(
      String id,
      String name,
      String description,
      SEVERITY severity,
      String category,
      String recurrence,
      Instant updatedAt,
      String[] tags,
      String[] platforms,
      String[] domains,
      String workflowId,
      String authorName,
      String version,
      Instant lastRunAt) {
    this.scenario_id = id;
    this.scenario_name = name;
    this.scenario_description = description;
    this.scenario_severity = severity;
    this.scenario_category = category;
    this.scenario_recurrence = recurrence;
    this.scenario_updated_at = updatedAt;
    this.scenario_tags = toSet(tags);
    this.scenario_platforms = toSet(platforms);
    this.scenario_domains = toSet(domains);
    this.scenario_workflow_id = workflowId;
    this.scenario_author_name = authorName;
    this.scenario_version = version;
    this.scenario_last_run_at = lastRunAt;
  }

  private static Set<String> toSet(String[] values) {
    return values != null ? new HashSet<>(Arrays.asList(values)) : new HashSet<>();
  }
}
