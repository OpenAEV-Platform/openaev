package io.openaev.rest.team.query_model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Builder
@Data
public class TeamQueryModel {

  @NotBlank
  private String id;

  @NotBlank
  private String name;

  private String description;

  private Boolean contextual;

  @NotNull
  private Instant updatedAt;

  @NotBlank
  private Set<String> exercises;

  @NotBlank
  private Set<String> scenarios;

  private Set<String> tags;

  private Set<String> users;

  private String organization;

  public long getUsersNumber() {
    return Optional.ofNullable(getUsers()).map(Collection::size).orElse(0);
  }
}
