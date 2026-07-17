package io.openaev.service.attackpath.ingestion;

import java.util.List;

/**
 * The primitives the source/target resolver needs, extracted from the executed inject at the hook.
 * Deliberately free of JPA entities so the resolver stays a pure, unit-testable function; the
 * extraction (reusing the engine's asset resolution) lives at the hook, not here.
 */
public record ResolutionInput(
    boolean withAgent,
    String injectorName, // the source, when injector-based (no agent)
    List<Endpoint> agentEndpoints, // the sources, when agent-based (one per executing agent)
    PayloadKind payloadKind,
    String dnsHostname, // DnsResolution target (raw)
    List<String> commandArgs, // Command raw argument values
    String contentSelector, // "assets" | "manual" (injector-based)
    List<String> manualTargets, // injector-based manual raw targets
    List<Endpoint> injectAssets) { // the inject's resolved asset targets

  /** Normalise null lists to empty so the resolver never has to null-check. */
  public ResolutionInput {
    agentEndpoints = agentEndpoints == null ? List.of() : agentEndpoints;
    commandArgs = commandArgs == null ? List.of() : commandArgs;
    manualTargets = manualTargets == null ? List.of() : manualTargets;
    injectAssets = injectAssets == null ? List.of() : injectAssets;
  }

  /** An endpoint (asset), optionally carrying the agent that ran on it. */
  public record Endpoint(
      String assetId,
      String hostname,
      String ip,
      String platform,
      String agentId,
      String agentName,
      String agentPrivilege) {}

  public enum PayloadKind {
    DNS_RESOLUTION,
    COMMAND,
    FILE,
    OTHER
  }
}
