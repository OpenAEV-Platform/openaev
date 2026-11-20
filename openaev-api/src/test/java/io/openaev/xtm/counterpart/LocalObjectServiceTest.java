package io.openaev.xtm.counterpart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ExerciseRepository.ScenarioSimulationsRow;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingRepository.FindingCountRow;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository.AlertInjectRow;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.xtm.counterpart.LocalObjectService.Access;
import io.openaev.xtm.counterpart.LocalObjectService.Fact;
import io.openaev.xtm.counterpart.LocalObjectService.LocalObject;
import io.openaev.xtm.counterpart.form.LocalObjectOutput;
import java.time.Instant;
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
  private FindingRepository findings;
  private ExerciseRepository exercises;
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
    findings = mock(FindingRepository.class);
    exercises = mock(ExerciseRepository.class);
    service =
        new LocalObjectService(
            config,
            assets,
            attackPatterns,
            vulnerabilities,
            scenarios,
            traces,
            findings,
            exercises);
  }

  @Test
  @DisplayName("an asset is found by its lower-cased hostname, with its deep link")
  void assetByHostname() {
    Asset asset = new Asset();
    asset.setId("a-1");
    asset.setName("Web server");
    asset.setHostname("WEB-01");
    when(assets.findAllByHostnameIn(Set.of("web-01"))).thenReturn(List.of(asset));
    when(findings.countFindingsByAssetIdIn(List.of("a-1"))).thenReturn(List.of(count("a-1", 3, 0)));

    List<LocalObject> found = service.find(CounterpartKind.ASSET, List.of("  Web-01 "), Access.ALL);

    assertThat(found)
        .containsExactly(
            new LocalObject(
                "web-01",
                "a-1",
                "asset",
                "Web server",
                "https://aev.example/admin/assets/a-1",
                List.of(new Fact("findings", "3"))));
  }

  @Test
  @DisplayName(
      "an asset without findings carries a zero findings fact, unmatched keys read nothing")
  void assetWithoutFindings() {
    Asset asset = new Asset();
    asset.setId("a-3");
    asset.setHostname("db-01");
    when(assets.findAllByHostnameIn(any())).thenReturn(List.of(asset));

    assertThat(service.find(CounterpartKind.ASSET, List.of("db-01"), Access.ALL).getFirst().facts())
        .containsExactly(new Fact("findings", "0"));

    when(assets.findAllByHostnameIn(any())).thenReturn(List.of());
    assertThat(service.find(CounterpartKind.ASSET, List.of("nowhere"), Access.ALL)).isEmpty();
    verify(findings, times(1)).countFindingsByAssetIdIn(any());
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
        service.find(CounterpartKind.ATTACK_PATTERN, List.of("t1059", "T1059"), Access.ALL);

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
    when(findings.countFindingsByTypeAndValueIn(ContractOutputType.CVE, Set.of("CVE-2024-1234")))
        .thenReturn(List.of(count("CVE-2024-1234", 5, 2)));

    List<LocalObject> found =
        service.find(CounterpartKind.VULNERABILITY, List.of("cve-2024-1234"), Access.ALL);

    assertThat(found).extracting(LocalObject::id).containsExactly("v-1");
    assertThat(found.getFirst().facts())
        .containsExactly(new Fact("findings", "5"), new Fact("affected_assets", "2"));
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

    Instant last = Instant.parse("2026-11-17T09:30:00Z");
    when(exercises.countSimulationsByScenarioIdIn(List.of("s-new")))
        .thenReturn(List.of(simulations("s-new", 4, last)));

    List<LocalObject> found =
        service.find(CounterpartKind.THREAT, List.of("intrusion-set--1"), Access.ALL);

    assertThat(found).extracting(LocalObject::id).containsExactly("s-new");
    assertThat(found.getFirst().url()).isEqualTo("https://aev.example/admin/scenarios/s-new");
    assertThat(found.getFirst().facts())
        .containsExactly(
            new Fact("simulations", "4"), new Fact("last_simulation", "2026-11-17T09:30:00Z"));
  }

  @Test
  @DisplayName("a scenario never simulated says so, without a last simulation date")
  void scenarioNeverSimulated() {
    Scenario s = new Scenario();
    s.setId("s-1");
    s.setExternalReference("malware--1");
    when(scenarios.findAllByExternalReferenceInOrderByUpdatedAtDesc(any())).thenReturn(List.of(s));

    assertThat(
            service
                .find(CounterpartKind.THREAT, List.of("malware--1"), Access.ALL)
                .getFirst()
                .facts())
        .containsExactly(new Fact("simulations", "0"));
  }

  @Test
  @DisplayName("the output carries the facts under object_facts, empty when there are none")
  void outputFacts() {
    LocalObjectOutput withFacts =
        LocalObjectOutput.from(
            new LocalObject("k", "i", "asset", "n", "/u", List.of(new Fact("findings", "1"))));
    assertThat(withFacts.facts())
        .containsExactly(new LocalObjectOutput.FactOutput("findings", "1"));
    assertThat(LocalObjectOutput.from(new LocalObject("k", "i", "inject", "n", "/u")).facts())
        .isEmpty();
  }

  private static FindingCountRow count(String key, long total, long affected) {
    return new FindingCountRow() {
      @Override
      public String getObjectKey() {
        return key;
      }

      @Override
      public long getFindings() {
        return total;
      }

      @Override
      public long getAssets() {
        return affected;
      }
    };
  }

  private static ScenarioSimulationsRow simulations(String scenarioId, long total, Instant last) {
    return new ScenarioSimulationsRow() {
      @Override
      public String getScenarioId() {
        return scenarioId;
      }

      @Override
      public long getSimulations() {
        return total;
      }

      @Override
      public Instant getLastStart() {
        return last;
      }
    };
  }

  @Test
  @DisplayName("a scenario the caller cannot read is never returned, the next readable one is")
  void threatWithoutGrant() {
    Scenario hidden = new Scenario();
    hidden.setId("s-hidden");
    hidden.setName("Hidden");
    hidden.setExternalReference("intrusion-set--1");
    Scenario granted = new Scenario();
    granted.setId("s-granted");
    granted.setName("Granted");
    granted.setExternalReference("intrusion-set--1");
    when(scenarios.findAllByExternalReferenceInOrderByUpdatedAtDesc(any()))
        .thenReturn(List.of(hidden, granted));

    List<LocalObject> found =
        service.find(
            CounterpartKind.THREAT,
            List.of("intrusion-set--1"),
            access(Set.of("s-granted"), Set.of(), Set.of(), true));
    assertThat(found).extracting(LocalObject::id).containsExactly("s-granted");

    assertThat(
            service.find(
                CounterpartKind.THREAT,
                List.of("intrusion-set--1"),
                access(Set.of(), Set.of(), Set.of(), true)))
        .isEmpty();
    verify(exercises, times(1)).countSimulationsByScenarioIdIn(any());
  }

  @Test
  @DisplayName("an alert whose simulation or atomic testing the caller cannot read is left out")
  void alertWithoutGrant() {
    when(traces.findAlertInjectsByAlertLinkIn(any()))
        .thenReturn(
            List.of(
                row("https://edr/alert/1", "i-new", "Hidden", "x-hidden"),
                row("https://edr/alert/1", "i-old", "Granted", "x-granted"),
                row("https://siem/a/9", "i-9", "Atomic", null)));

    List<LocalObject> found =
        service.find(
            CounterpartKind.ALERT,
            List.of("https://edr/alert/1", "https://siem/a/9"),
            access(Set.of(), Set.of("x-granted"), Set.of(), true));

    assertThat(found).extracting(LocalObject::id).containsExactly("i-old");
  }

  @Test
  @DisplayName("a caller without the findings right gets no finding counts and none are read")
  void findingsWithoutRight() {
    Asset asset = new Asset();
    asset.setId("a-1");
    asset.setHostname("web-01");
    when(assets.findAllByHostnameIn(any())).thenReturn(List.of(asset));
    Vulnerability v = new Vulnerability();
    v.setId("v-1");
    v.setExternalId("CVE-2024-1234");
    when(vulnerabilities.getVulnerabilitiesByExternalIds(any())).thenReturn(List.of(v));
    Access noFindings = access(Set.of(), Set.of(), Set.of(), false);

    assertThat(
            service.find(CounterpartKind.ASSET, List.of("web-01"), noFindings).getFirst().facts())
        .isEmpty();
    assertThat(
            service
                .find(CounterpartKind.VULNERABILITY, List.of("CVE-2024-1234"), noFindings)
                .getFirst()
                .facts())
        .isEmpty();
    verify(findings, never()).countFindingsByAssetIdIn(any());
    verify(findings, never()).countFindingsByTypeAndValueIn(any(), any());
  }

  private static Access access(
      Set<String> scenarioIds,
      Set<String> simulationIds,
      Set<String> atomicTestingIds,
      boolean findings) {
    return new Access() {
      @Override
      public boolean canReadScenario(String scenarioId) {
        return scenarioIds.contains(scenarioId);
      }

      @Override
      public boolean canReadSimulation(String simulationId) {
        return simulationIds.contains(simulationId);
      }

      @Override
      public boolean canReadAtomicTesting(String injectId) {
        return atomicTestingIds.contains(injectId);
      }

      @Override
      public boolean canReadFindings() {
        return findings;
      }
    };
  }

  @Test
  @DisplayName("blank keys read nothing")
  void blankKeys() {
    assertThat(service.find(CounterpartKind.ASSET, List.of(" ", ""), Access.ALL)).isEmpty();
    assertThat(service.find(CounterpartKind.ALERT, List.of(" "), Access.ALL)).isEmpty();
    verifyNoInteractions(
        assets, attackPatterns, vulnerabilities, scenarios, traces, findings, exercises);
  }

  @Test
  @DisplayName("an alert link resolves to the inject of its newest trace, in its simulation")
  void alertToSimulationInject() {
    when(traces.findAlertInjectsByAlertLinkIn(Set.of("https://edr/alert/1")))
        .thenReturn(
            List.of(
                row("https://edr/alert/1", "i-new", "Dump LSASS", "x-1"),
                row("https://edr/alert/1", "i-old", "Older", "x-0")));

    List<LocalObject> found =
        service.find(CounterpartKind.ALERT, List.of(" https://edr/alert/1 "), Access.ALL);

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

    assertThat(
            service
                .find(CounterpartKind.ALERT, List.of("https://siem/a/9"), Access.ALL)
                .getFirst()
                .url())
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

    assertThat(service.find(CounterpartKind.ASSET, List.of("laptop"), Access.ALL).getFirst().url())
        .isEqualTo("/admin/assets/a-2");
  }
}
