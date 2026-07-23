package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.service.attackpath.ingestion.ResolutionInput.Endpoint;
import io.openaev.service.attackpath.ingestion.ResolutionInput.PayloadKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the source/target resolution (issue 5048, #203). */
class AttackPathSourceTargetResolverTest {

  private final AttackPathSourceTargetResolver resolver = new AttackPathSourceTargetResolver();

  private static Endpoint asset(String id, String host, String ip, String platform) {
    return new Endpoint(id, host, ip, platform, null, null, null);
  }

  private static Endpoint agent(
      String assetId,
      String host,
      String ip,
      String platform,
      String agentId,
      String name,
      String priv) {
    return new Endpoint(assetId, host, ip, platform, agentId, name, priv);
  }

  @Test
  @DisplayName(
      "Injector-based, selector 'assets': one edge per asset, source = injector, target frozen")
  void injectorAssets() {
    ResolutionInput input =
        new ResolutionInput(
            false,
            "Nmap",
            List.of(),
            PayloadKind.OTHER,
            null,
            List.of(),
            "assets",
            List.of(),
            List.of(
                asset("a1", "host1", "10.0.0.1", "Linux"),
                asset("a2", "host2", "10.0.0.2", "Windows")));

    List<ResolvedExecutionEdge> edges = resolver.resolve(input);

    assertThat(edges).hasSize(2);
    assertThat(edges)
        .allSatisfy(
            e -> {
              assertThat(e.sourceKind()).isEqualTo("INJECTOR");
              assertThat(e.sourceInjector()).isEqualTo("Nmap");
              assertThat(e.sourceAssetId()).isNull();
              assertThat(e.sourceHostname()).isNull();
              assertThat(e.targetKind()).isEqualTo("ASSET");
              assertThat(e.agentName()).isNull();
            });
    assertThat(edges.get(0).targetAssetId()).isEqualTo("a1");
    assertThat(edges.get(0).targetKey()).isEqualTo("a1");
    assertThat(edges.get(0).targetHostname()).isEqualTo("host1");
    assertThat(edges.get(0).targetPlatform()).isEqualTo("Linux");
  }

  @Test
  @DisplayName("Injector-based, selector 'manual': raw target, DISCOVERED kind, key = raw, no host")
  void injectorManual() {
    ResolutionInput input =
        new ResolutionInput(
            false,
            "NetExec",
            List.of(),
            PayloadKind.OTHER,
            null,
            List.of(),
            "manual",
            List.of("honey.scanme.sh"),
            List.of());

    List<ResolvedExecutionEdge> edges = resolver.resolve(input);

    assertThat(edges).hasSize(1);
    ResolvedExecutionEdge e = edges.get(0);
    assertThat(e.sourceKind()).isEqualTo("INJECTOR");
    assertThat(e.targetKind()).isEqualTo("DISCOVERED");
    assertThat(e.targetAssetId()).isNull();
    assertThat(e.targetRawValue()).isEqualTo("honey.scanme.sh");
    assertThat(e.targetKey()).isEqualTo("honey.scanme.sh");
    assertThat(e.targetHostname()).isNull();
  }

  @Test
  @DisplayName(
      "Agent-based, DnsResolution: source = agent endpoint, target = dns hostname (DISCOVERED)")
  void agentDns() {
    ResolutionInput input =
        new ResolutionInput(
            true,
            null,
            List.of(
                agent(
                    "agent-asset-1",
                    "corp-dc",
                    "10.0.0.5",
                    "Windows",
                    "agt-1",
                    "agent-1",
                    "admin")),
            PayloadKind.DNS_RESOLUTION,
            "internal.corp",
            List.of(),
            null,
            List.of(),
            List.of());

    List<ResolvedExecutionEdge> edges = resolver.resolve(input);

    assertThat(edges).hasSize(1);
    ResolvedExecutionEdge e = edges.get(0);
    assertThat(e.sourceKind()).isEqualTo("AGENT_ASSET");
    assertThat(e.sourceAssetId()).isEqualTo("agent-asset-1");
    assertThat(e.sourceHostname()).isEqualTo("corp-dc");
    assertThat(e.sourceIp()).isEqualTo("10.0.0.5");
    assertThat(e.sourcePlatform()).isEqualTo("Windows");
    assertThat(e.sourceInjector()).isNull();
    assertThat(e.targetKind()).isEqualTo("DISCOVERED");
    assertThat(e.targetRawValue()).isEqualTo("internal.corp");
    assertThat(e.targetKey()).isEqualTo("internal.corp");
    assertThat(e.agentName()).isEqualTo("agent-1");
    assertThat(e.agentPrivilege()).isEqualTo("admin");
  }

  @Test
  @DisplayName("Agent-based, Command with an inject asset: source = agent, target = the asset")
  void agentCommandAsset() {
    ResolutionInput input =
        new ResolutionInput(
            true,
            null,
            List.of(
                agent(
                    "agent-asset-1",
                    "corp-dc",
                    "10.0.0.5",
                    "Windows",
                    "agt-1",
                    "agent-1",
                    "admin")),
            PayloadKind.COMMAND,
            null,
            List.of("--target victim"),
            null,
            List.of(),
            List.of(asset("victim-1", "victim", "10.0.0.9", "Linux")));

    List<ResolvedExecutionEdge> edges = resolver.resolve(input);

    assertThat(edges).hasSize(1);
    ResolvedExecutionEdge e = edges.get(0);
    assertThat(e.sourceKind()).isEqualTo("AGENT_ASSET");
    assertThat(e.sourceAssetId()).isEqualTo("agent-asset-1");
    assertThat(e.targetKind()).isEqualTo("ASSET");
    assertThat(e.targetAssetId()).isEqualTo("victim-1");
    assertThat(e.targetKey()).isEqualTo("victim-1");
    assertThat(e.targetHostname()).isEqualTo("victim");
    assertThat(e.agentName()).isEqualTo("agent-1");
  }

  @Test
  @DisplayName("Agent-based, File on two assets: one edge per target asset, source = the agent")
  void agentFileTwoAssets() {
    ResolutionInput input =
        new ResolutionInput(
            true,
            null,
            List.of(
                agent(
                    "agent-asset-1", "corp-dc", "10.0.0.5", "Windows", "agt-1", "agent-1", "user")),
            PayloadKind.FILE,
            null,
            List.of(),
            null,
            List.of(),
            List.of(
                asset("a1", "host1", "10.0.0.1", "Linux"),
                asset("a2", "host2", "10.0.0.2", "Windows")));

    List<ResolvedExecutionEdge> edges = resolver.resolve(input);

    assertThat(edges).hasSize(2);
    assertThat(edges)
        .allSatisfy(
            e -> {
              assertThat(e.sourceKind()).isEqualTo("AGENT_ASSET");
              assertThat(e.sourceAssetId()).isEqualTo("agent-asset-1");
              assertThat(e.targetKind()).isEqualTo("ASSET");
              assertThat(e.agentName()).isEqualTo("agent-1");
            });
    assertThat(edges).extracting(ResolvedExecutionEdge::targetAssetId).containsExactly("a1", "a2");
  }

  @Test
  @DisplayName("Null lists are tolerated (normalised to empty), no NPE")
  void nullListsTolerated() {
    ResolutionInput input =
        new ResolutionInput(
            false, "Nmap", null, PayloadKind.OTHER, null, null, "assets", null, null);

    // Robustness only: with no assets there is no edge (the targetless/local-execution semantics
    // are
    // an open product question, tracked in the plan — not asserted as correct here).
    assertThat(resolver.resolve(input)).isEmpty();
  }
}
