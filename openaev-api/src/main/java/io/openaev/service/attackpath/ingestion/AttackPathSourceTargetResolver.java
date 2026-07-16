package io.openaev.service.attackpath.ingestion;

import io.openaev.service.attackpath.ingestion.ResolutionInput.Endpoint;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves an executed inject into attack-path edges (source → target), per the product's
 * source/target rules (issue 5048, #203). Pure: no persistence, no entities. Extracting {@link
 * ResolutionInput} from the live inject (reusing the engine's asset resolution) happens at the
 * hook.
 *
 * <p>Source: injector-based → the injector; agent-based → each executing agent's endpoint. Target
 * by payload: DnsResolution → the resolved hostname (raw); File → the inject's assets; Command →
 * the inject's assets, else the raw argument values; injector selector {@code assets}/{@code
 * manual} → the inject's assets / the raw manual targets.
 */
@Component
public class AttackPathSourceTargetResolver {

  static final String SOURCE_INJECTOR = "INJECTOR";
  static final String SOURCE_AGENT_ASSET = "AGENT_ASSET";
  static final String TARGET_ASSET = "ASSET";
  static final String TARGET_DISCOVERED = "DISCOVERED";
  static final String SELECTOR_MANUAL = "manual";

  public List<ResolvedExecutionEdge> resolve(ResolutionInput input) {
    List<ResolvedTarget> targets = resolveTargets(input);
    List<ResolvedExecutionEdge> edges = new ArrayList<>();
    if (input.withAgent()) {
      // One edge per (executing agent's endpoint, target): source = the agent's endpoint. The
      // extraction (the hook) is responsible for passing the correct agent/target pairing; this
      // pure step takes the cross product of what it is given.
      for (Endpoint agent : input.agentEndpoints()) {
        for (ResolvedTarget t : targets) {
          edges.add(
              edge(
                  SOURCE_AGENT_ASSET,
                  null,
                  agent.assetId(),
                  agent.hostname(),
                  agent.ip(),
                  agent.platform(),
                  t,
                  agent.agentId(),
                  agent.agentName(),
                  agent.agentPrivilege()));
        }
      }
    } else {
      // Injector-based: source = the injector.
      for (ResolvedTarget t : targets) {
        edges.add(
            edge(
                SOURCE_INJECTOR,
                input.injectorName(),
                null,
                null,
                null,
                null,
                t,
                null,
                null,
                null));
      }
    }
    return edges;
  }

  private List<ResolvedTarget> resolveTargets(ResolutionInput input) {
    if (input.withAgent()) {
      return switch (input.payloadKind()) {
        case DNS_RESOLUTION ->
            input.dnsHostname() == null ? List.of() : List.of(rawTarget(input.dnsHostname()));
        case COMMAND ->
            input.injectAssets().isEmpty()
                ? input.commandArgs().stream().map(this::rawTarget).toList()
                : assetTargets(input.injectAssets());
        case FILE, OTHER -> assetTargets(input.injectAssets());
      };
    }
    if (SELECTOR_MANUAL.equals(input.contentSelector())) {
      return input.manualTargets().stream().map(this::rawTarget).toList();
    }
    return assetTargets(input.injectAssets());
  }

  private List<ResolvedTarget> assetTargets(List<Endpoint> assets) {
    return assets.stream()
        .map(
            a ->
                new ResolvedTarget(
                    TARGET_ASSET,
                    a.assetId(),
                    null,
                    a.assetId(),
                    a.hostname(),
                    a.ip(),
                    a.platform()))
        .toList();
  }

  private ResolvedTarget rawTarget(String raw) {
    return new ResolvedTarget(TARGET_DISCOVERED, null, raw, raw, null, null, null);
  }

  private ResolvedExecutionEdge edge(
      String sourceKind,
      String sourceInjector,
      String sourceAssetId,
      String sourceHostname,
      String sourceIp,
      String sourcePlatform,
      ResolvedTarget t,
      String agentId,
      String agentName,
      String agentPrivilege) {
    return new ResolvedExecutionEdge(
        sourceKind,
        sourceInjector,
        sourceAssetId,
        sourceHostname,
        sourceIp,
        sourcePlatform,
        t.kind(),
        t.assetId(),
        t.rawValue(),
        t.key(),
        t.hostname(),
        t.ip(),
        t.platform(),
        agentId,
        agentName,
        agentPrivilege);
  }

  private record ResolvedTarget(
      String kind,
      String assetId,
      String rawValue,
      String key,
      String hostname,
      String ip,
      String platform) {}
}
