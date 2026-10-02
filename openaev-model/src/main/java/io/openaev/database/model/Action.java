package io.openaev.database.model;

public enum Action {
  READ,
  WRITE,
  LAUNCH,
  // Following actions should only be used for first level of API.
  // For sub-resources, use the parent resource's actions instead.
  // Example: To delete an article from a scenario, you need the WRITE on the scenario resource, not
  // DELETE on the article resource.
  DELETE,
  SEARCH,
  /**
   * TEMPORARY: introduced to scope service-account document access without SEARCH capability. See
   * #294 for the durable per-document scoping solution. Remove once #294 is implemented.
   */
  AGENT_DOCUMENT_READ,
  CREATE,
  DUPLICATE,

  // Special actions for specific use cases
  SKIP_RBAC, // Used to skip RBAC checks in specific cases

  // specific to stix bundle processing
  PROCESS,

  // specific for resolve credential from injectors
  RESOLVE,

  // specific to login/logout
  LOGIN,
  LOGOUT,
  UNAUTHORIZED
}
