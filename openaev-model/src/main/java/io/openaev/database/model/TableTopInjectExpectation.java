package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public abstract class TableTopInjectExpectation extends BaseInjectExpectation {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_user")
  @Schema(implementation = String.class)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "team_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_team")
  @Schema(implementation = String.class)
  private Team team;

  public boolean isUserHasAccess(User user) {
    if (getExercise() != null) {
      return getExercise().isUserHasAccess(user);
    }
    return user.isAdmin();
  }
}
