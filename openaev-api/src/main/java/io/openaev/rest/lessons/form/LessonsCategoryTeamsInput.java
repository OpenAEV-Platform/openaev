package io.openaev.rest.lessons.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class LessonsCategoryTeamsInput {

  @JsonProperty("lessons_category_teams")
  private List<String> teamIds;

  public List<String> getTeamIds() {
    return teamIds;
  }

  public void setTeamIds(List<String> teamIds) {
    this.teamIds = teamIds;
  }
}
