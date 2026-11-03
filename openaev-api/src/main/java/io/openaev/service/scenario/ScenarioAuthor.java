package io.openaev.service.scenario;

/** The author of a scenario: a user or an organization, with its display name. */
public record ScenarioAuthor(String id, String name, String type) {

  public static final String USER = "user";
  public static final String ORGANIZATION = "organization";
}
