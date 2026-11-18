package io.openaev.xtm.counterpart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository.AlertInjectRow;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.xtm.counterpart.LocalObjectService.LocalObject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalObjectServiceTest {

  private OpenAEVConfig config;
  private AssetRepository assets;
  private AttackPatternService attackPatterns;
  private VulnerabilityService vulnerabilities;
  private ScenarioRepository scenarios;
  private InjectExpectationTraceRepository traces;
  private LocalObjectService service;

  @BeforeEach
  void setUp() {
    config = new OpenAEVConfig();
    config.setBaseUrl("https://aev.example/");
    assets = mock(AssetRepository.class);
    attackPatterns = mock(AttackPatternService.class);
    vulnerabilities = mock(VulnerabilityService.class);
    scenarios = mock(ScenarioRepository.class);
    traces = mock(InjectExpectationTraceRepository.class);
    service =
        new LocalObjectService(config, assets, attackPatterns, vulnerabilities, scenarios, traces);
  }

  @Test
  @DisplayName("an asset is found by its lower-cased hostname, with its deep link")
  void assetByHostname() {
    Asset asset = new Asset();
    asset.setId("a-1");
    asset.setName("Web server");
    asset.setHostname("WEB-01");
    when(assets.findAllByHostnameIn(Set.of("web-01"))).thenReturn(List.of(asset));

    List<LocalObject> found = service.find(CounterpartKind.ASSET, List.of("  Web-01 "));

    assertThat(found)
        .containsExactly(
            new LocalObject(
                "web-01", "a-1", "asset", "Web server", "https://aev.example/admin/assets/a-1"));
  }

  @Test
  @DisplayName("an attack pattern matches its MITRE id whatever the case, once per key")
  void attackPatternByMitreId() {
    AttackPattern ap = new AttackPattern();
    ap.setId("ap-1");
    ap.setName("Command and Scripting Interpreter");
    ap.setExternalId("t1059");
    when(attackPatterns.getAttackPatternsByExternalIds(Set.of("T1059")))
        .thenReturn(List.of(ap, ap));

    List<LocalObject> found =
        service.find(CounterpartKind.ATTACK_PATTERN, List.of("t1059", "T1059"));

    assertThat(found).hasSize(1);
    assertThat(found.getFirst().key()).isEqualTo("T1059");
    assertThat(found.getFirst().url())
        .isEqualTo("https://aev.example/admin/settings/taxonomies/attack_patterns");
  }

  @Test
  @DisplayName("a vulnerability matches its CVE id")
  void vulnerabilityByCve() {
    Vulnerability v = new Vulnerability();
    v.setId("v-1");
    v.setExternalId("CVE-2024-1234");
    when(vulnerabilities.getVulnerabilitiesByExternalIds(Set.of("CVE-2024-1234")))
        .thenReturn(Set.of(v));

    List<LocalObject> found = service.find(CounterpartKind.VULNERABILITY, List.of("cve-2024-1234"));

    assertThat(found).extracting(LocalObject::id).containsExactly("v-1");
  }

  @Test
  @DisplayName("a threat STIX id resolves to the most recent scenario generated from it")
  void threatToLatestScenario() {
    Scenario recent = new Scenario();
    recent.setId("s-new");
    recent.setName("Recent");
    recent.setExternalReference("intrusion-set--1");
    Scenario old = new Scenario();
    old.setId("s-old");
    old.setName("Old");
    old.setExternalReference("intrusion-set--1");
    when(scenarios.findAllByExternalReferenceInOrderByUpdatedAtDesc(Set.of("intrusion-set--1")))
        .thenReturn(List.of(recent, old));

    List<LocalObject> found = service.find(CounterpartKind.THREAT, List.of("intrusion-set--1"));

    assertThat(found).extracting(LocalObject::id).containsExactly("s-new");
    assertThat(found.getFirst().url()).isEqualTo("https://aev.example/admin/scenarios/s-new");
  }

  @Test
  @DisplayName("blank keys read nothing")
  void blankKeys() {
    assertThat(service.find(CounterpartKind.ASSET, List.of(" ", ""))).isEmpty();
    assertThat(service.find(CounterpartKind.ALERT, List.of(" "))).isEmpty();
    verifyNoInteractions(assets, attackPatterns, vulnerabilities, scenarios, traces);
  }

  @Test
  @DisplayName("an alert link resolves to the inject of its newest trace, in its simulation")
  void alertToSimulationInject() {
    when(traces.findAlertInjectsByAlertLinkIn(Set.of("https://edr/alert/1")))
        .thenReturn(
            List.of(
                row("https://edr/alert/1", "i-new", "Dump LSASS", "x-1"),
                row("https://edr/alert/1", "i-old", "Older", "x-0")));

    List<LocalObject> found = service.find(CounterpartKind.ALERT, List.of(" https://edr/alert/1 "));

    assertThat(found)
        .containsExactly(
            new LocalObject(
                "https://edr/alert/1",
                "i-new",
                "inject",
                "Dump LSASS",
                "https://aev.example/admin/simulations/x-1/injects/i-new"));
  }

  @Test
  @DisplayName("an alert of an atomic testing links to the atomic testing")
  void alertToAtomicTesting() {
    when(traces.findAlertInjectsByAlertLinkIn(any()))
        .thenReturn(List.of(row("https://siem/a/9", "i-9", "Atomic", null)));

    assertThat(service.find(CounterpartKind.ALERT, List.of("https://siem/a/9")).getFirst().url())
        .isEqualTo("https://aev.example/admin/atomic_testings/i-9");
  }

  private static AlertInjectRow row(
      String alertLink, String injectId, String injectTitle, String exerciseId) {
    return new AlertInjectRow() {
      @Override
      public String getAlertLink() {
        return alertLink;
      }

      @Override
      public String getInjectId() {
        return injectId;
      }

      @Override
      public String getInjectTitle() {
        return injectTitle;
      }

      @Override
      public String getExerciseId() {
        return exerciseId;
      }
    };
  }

  @Test
  @DisplayName("without a base URL the link stays a relative path")
  void relativeLinkWithoutBaseUrl() {
    config.setBaseUrl(null);
    Asset asset = new Asset();
    asset.setId("a-2");
    asset.setName("Laptop");
    asset.setHostname("laptop");
    when(assets.findAllByHostnameIn(any())).thenReturn(List.of(asset));

    assertThat(service.find(CounterpartKind.ASSET, List.of("laptop")).getFirst().url())
        .isEqualTo("/admin/assets/a-2");
  }
}
