package io.openaev.database.model;

public enum Action {
  READ,
  WRITE,
  LAUNCH,
  // Following actions should only be used for first level of API.
  // For sub-resources, use the parent resource's actions instead.
  // Exemple: To delete an article form a scenario, you need the WRITE on the scenario resource, not
  // DELETE on the article resource.
  DELETE,
  SEARCH,
  CREATE,
  DUPLICATE,

  // Special actions for specific use cases
  SKIP_RBAC, // Used to skip RBAC checks in specific cases
}
