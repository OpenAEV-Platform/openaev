package io.openaev.database.model;

/** Analyst-oriented grouping derived from a finding's contract output type. */
public enum FindingAggregationCategory {
  SURFACE_REACHABILITY,
  IDENTITIES,
  CREDENTIAL_ACCESS,
  PRIVILEGE_TRUST_STRUCTURE,
  EXPLOITABLE_WEAKNESSES,
  RESOURCES,
  CONFIGURATION_POSTURE,
  INFORMATIVE;

  public static FindingAggregationCategory from(ContractOutputType type) {
    return switch (type) {
      case Port, PortsScan, IPv4, IPv6, Computer -> SURFACE_REACHABILITY;
      case Sid, Username, AdminUsername, Email -> IDENTITIES;
      case Credentials,
              AccountWithPasswordNotRequired,
              AsreproastableAccount,
              KerberoastableAccount ->
          CREDENTIAL_ACCESS;
      case Group, Delegation -> PRIVILEGE_TRUST_STRUCTURE;
      case CVE, Vulnerability -> EXPLOITABLE_WEAKNESSES;
      case Share, File -> RESOURCES;
      case PasswordPolicy, OCSF -> CONFIGURATION_POSTURE;
      case Text, Number, ActionOutput, ExpectationSignature, Asset -> INFORMATIVE;
    };
  }
}
