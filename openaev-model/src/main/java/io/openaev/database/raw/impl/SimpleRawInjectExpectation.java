package io.openaev.database.raw.impl;

import io.openaev.database.raw.RawInjectExpectation;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleRawInjectExpectation implements RawInjectExpectation {

  private String inject_expectation_id;
  private String inject_expectation_type;
  private Double inject_expectation_score;
  private Double inject_expectation_expected_score;
  private String team_id;
  private String team_name;
  private String user_id;
  private String user_firstname;
  private String user_lastname;
  private String agent_id;
  private String asset_id;
  private String asset_name;
  private String asset_type;
  private String asset_external_reference;
  private String endpoint_platform;
  private String asset_group_id;
  private String asset_group_name;
  private List<String> asset_ids;
  private String scenario_id;
  private String exercise_id;
  private String inject_id;
  private Boolean inject_expectation_group;
  private Instant inject_expectation_created_at;
  private String inject_expectation_name;
  public String inject_expectation_description;
  public String inject_expectation_results;
  public Long inject_expiration_time;
  public Instant inject_expectation_updated_at;
  public Set<String> attack_pattern_ids;
  public Set<String> security_platform_ids;
  private String inject_title;
  private Instant tracking_sent_date;
}
