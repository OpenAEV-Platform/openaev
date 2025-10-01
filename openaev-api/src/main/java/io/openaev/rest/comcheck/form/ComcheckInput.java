package io.openaev.rest.comcheck.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ComcheckInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("comcheck_name")
  private String name;

  @JsonProperty("comcheck_end_date")
  private Instant end;

  @JsonProperty("comcheck_subject")
  private String subject;

  @JsonProperty("comcheck_message")
  private String message;

  @JsonProperty("comcheck_teams")
  private List<String> teamIds = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getTeamIds() {
    return teamIds;
  }

  public void setTeamIds(List<String> teamIds) {
    this.teamIds = teamIds;
  }

  public Instant getEnd() {
    return end;
  }

  public void setEnd(Instant end) {
    this.end = end;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
