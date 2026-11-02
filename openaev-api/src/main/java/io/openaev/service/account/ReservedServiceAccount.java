package io.openaev.service.account;

import io.openaev.database.model.Capability;
import java.util.Set;

/**
 * The service accounts the platform provisions on its own. Every entry is reserved by {@link
 * ReservedKeyValidator}: its role and group cannot be edited or deleted, and its email pattern
 * keeps the account out of player lists and team membership. Declaring an account here is what
 * reserves it.
 */
public enum ReservedServiceAccount {
  AGENT(
      new WellKnownAccess(
          "b2668023-1a92-4d7d-9f98-5e7bef6eae7c",
          "Service integration",
          "Allows running an agent/implant with the minimal required API endpoints.",
          // TEMPORARY (#294): ACCESS_DOCUMENTS replaced by AGENT_DOCUMENT_ACCESS to remove
          // the SEARCH capability (platform-wide document listing) from the service-account
          // role while preserving read-by-known-id via DocumentAccessControl OR-gate.
          Set.of(
              Capability.AGENT_RUNTIME_ACCESS,
              Capability.AGENT_DOCUMENT_ACCESS,
              Capability.INSTALL_AGENT),
          "3768d85b-dc56-4bd4-a844-2851dc41e3a8",
          "Service integration",
          "Group for granting access rights to the Agent/Implant API"),
      "service-%s@openaev.invalid"),

  OPENCTI_CONNECTOR(
      new WellKnownAccess(
          "2c24790b-fa69-4565-8dc8-b00f85ca47d5",
          "STIX bundle processors",
          "Can process STIX bundles via API",
          Set.of(Capability.MANAGE_STIX_BUNDLE),
          "0b4db570-fdf4-44e9-8daa-39130189fec8",
          "STIX bundle processors",
          "Group for granting access rights to the STIX bundle API"),
      "connector-opencti-%s@openaev.invalid");

  private final WellKnownAccess access;

  /** Email of the account, with {@code %s} standing for its tenant (agent) or connector id. */
  private final String emailPattern;

  ReservedServiceAccount(WellKnownAccess access, String emailPattern) {
    this.access = access;
    this.emailPattern = emailPattern;
  }

  public WellKnownAccess access() {
    return access;
  }

  public String emailPattern() {
    return emailPattern;
  }
}
