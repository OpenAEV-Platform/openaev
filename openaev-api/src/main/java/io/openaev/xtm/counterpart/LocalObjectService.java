package io.openaev.xtm.counterpart;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.repository.ExerciseRepository.ScenarioSimulationsRow;
import io.openaev.database.repository.FindingRepository.FindingCountRow;
import io.openaev.database.repository.InjectExpectationTraceRepository.AlertInjectRow;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.service.AssetService;
import io.openaev.service.InjectExpectationTraceService;
import io.openaev.service.scenario.ScenarioService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The reverse lookup of the counterpart contract: which of the given keys THIS OpenAEV holds, with
 * the deep link that opens each object. OpenCTI (and any other XTM product) calls it to show "Open
 * in OpenAEV" on its own objects, with the same keys {@link CounterpartKind} normalises.
 *
 * <p>Reads go through the request's transaction, so the statement inspector restricts the rows to
 * the caller's tenant scope and markings: a product connected with a user token sees what that user
 * sees, nothing more. Every read goes through the owning domain's service, never another domain's
 * repository; attack patterns and vulnerabilities are tenant-active tables read through their
 * reviewed services (TenantActiveTableAccessArchTest).
 *
 * <p>Scenarios, simulations and atomic testings are grant-managed: the statement inspector does not
 * filter them by grant, so every scenario and inject this lookup returns is kept only when {@link
 * Access} says the caller can read it. Finding counts are shown only to a caller who can read
 * findings.
 */
@Service
@RequiredArgsConstructor
public class LocalObjectService {

  /**
   * One key fact of a matched object, shown next to the pivot by the product that asked: a stable
   * key the caller translates (findings, affected_assets, simulations, last_simulation) and its
   * value as text (a count, or an ISO-8601 instant).
   */
  public record Fact(String key, String value) {}

  /** One object of this OpenAEV matching a key, with its key facts. */
  public record LocalObject(
      String key, String id, String type, String name, String url, List<Fact> facts) {

    public LocalObject(String key, String id, String type, String name, String url) {
      this(key, id, type, name, url, List.of());
    }
  }

  /**
   * The caller's read rights on the grant-managed objects and on findings, evaluated by the
   * controller in the request's tenant scope.
   */
  public interface Access {

    boolean canReadScenario(String scenarioId);

    boolean canReadSimulation(String simulationId);

    boolean canReadAtomicTesting(String injectId);

    boolean canReadFindings();

    /** Everything readable: for callers that gate elsewhere. */
    Access ALL =
        new Access() {
          @Override
          public boolean canReadScenario(String scenarioId) {
            return true;
          }

          @Override
          public boolean canReadSimulation(String simulationId) {
            return true;
          }

          @Override
          public boolean canReadAtomicTesting(String injectId) {
            return true;
          }

          @Override
          public boolean canReadFindings() {
            return true;
          }
        };
  }

  static final String FACT_FINDINGS = "findings";
  static final String FACT_AFFECTED_ASSETS = "affected_assets";
  static final String FACT_SIMULATIONS = "simulations";
  static final String FACT_LAST_SIMULATION = "last_simulation";

  private final OpenAEVConfig openAEVConfig;
  private final AssetService assetService;
  private final AttackPatternService attackPatternService;
  private final VulnerabilityService vulnerabilityService;
  private final ScenarioService scenarioService;
  private final InjectExpectationTraceService injectExpectationTraceService;
  private final FindingService findingService;
  private final ExerciseService exerciseService;

  public List<LocalObject> find(CounterpartKind kind, Collection<String> keys, Access access) {
    Set<String> normalised = normalise(kind, keys);
    if (normalised.isEmpty()) {
      return List.of();
    }
    return switch (kind) {
      case ASSET -> assets(normalised, access);
      case ATTACK_PATTERN -> attackPatterns(normalised);
      case VULNERABILITY -> vulnerabilities(normalised, access);
      case THREAT -> scenarios(normalised, access);
      case ALERT -> alerts(normalised, access);
    };
  }

  static Set<String> normalise(CounterpartKind kind, Collection<String> keys) {
    Set<String> out = new LinkedHashSet<>();
    for (String key : keys) {
      String n = kind.normalise(key);
      if (n != null) {
        out.add(n);
      }
      if (out.size() >= CounterpartService.MAX_KEYS) {
        break;
      }
    }
    return out;
  }

  private List<LocalObject> assets(Set<String> hostnames, Access access) {
    // Hostnames are stored lower-case (Asset#setHostname), the ASSET kind normalises the same way.
    Map<String, Asset> byKey = new LinkedHashMap<>();
    for (Asset asset : assetService.findAllByHostnameIn(hostnames)) {
      byKey.putIfAbsent(asset.getHostname(), asset);
    }
    if (byKey.isEmpty()) {
      return List.of();
    }
    boolean showFindings = access.canReadFindings();
    // One grouped read for the findings of every matched asset, for a caller who reads findings.
    Map<String, Long> findings = new HashMap<>();
    if (showFindings) {
      for (FindingCountRow row :
          findingService.countFindingsByAssetIdIn(
              byKey.values().stream().map(Asset::getId).toList())) {
        findings.put(row.getObjectKey(), row.getFindings());
      }
    }
    List<LocalObject> out = new ArrayList<>();
    for (Asset asset : byKey.values()) {
      out.add(
          new LocalObject(
              asset.getHostname(),
              asset.getId(),
              "asset",
              asset.getName(),
              link("/admin/assets/" + asset.getId()),
              showFindings
                  ? List.of(
                      new Fact(
                          FACT_FINDINGS, String.valueOf(findings.getOrDefault(asset.getId(), 0L))))
                  : List.of()));
    }
    return out;
  }

  private List<LocalObject> attackPatterns(Set<String> mitreIds) {
    Map<String, LocalObject> byKey = new LinkedHashMap<>();
    for (AttackPattern ap : attackPatternService.getAttackPatternsByExternalIds(mitreIds)) {
      String key = ap.getExternalId().toUpperCase(Locale.ROOT);
      byKey.putIfAbsent(
          key,
          new LocalObject(
              key,
              ap.getId(),
              "attack_pattern",
              ap.getName(),
              link("/admin/settings/taxonomies/attack_patterns")));
    }
    return new ArrayList<>(byKey.values());
  }

  private List<LocalObject> vulnerabilities(Set<String> cves, Access access) {
    Map<String, Vulnerability> byKey = new LinkedHashMap<>();
    for (Vulnerability v : vulnerabilityService.getVulnerabilitiesByExternalIds(cves)) {
      byKey.putIfAbsent(v.getExternalId().toUpperCase(Locale.ROOT), v);
    }
    if (byKey.isEmpty()) {
      return List.of();
    }
    boolean showFindings = access.canReadFindings();
    // One grouped read: the CVE findings of the simulations, and the assets they were found on.
    Map<String, FindingCountRow> findings = new HashMap<>();
    if (showFindings) {
      for (FindingCountRow row :
          findingService.countFindingsByTypeAndValueIn(ContractOutputType.CVE, byKey.keySet())) {
        findings.put(row.getObjectKey(), row);
      }
    }
    List<LocalObject> out = new ArrayList<>();
    byKey.forEach(
        (key, v) -> {
          FindingCountRow row = findings.get(key);
          out.add(
              new LocalObject(
                  key,
                  v.getId(),
                  "vulnerability",
                  v.getExternalId(),
                  link("/admin/settings/taxonomies/vulnerabilities"),
                  showFindings
                      ? List.of(
                          new Fact(
                              FACT_FINDINGS, String.valueOf(row == null ? 0 : row.getFindings())),
                          new Fact(
                              FACT_AFFECTED_ASSETS,
                              String.valueOf(row == null ? 0 : row.getAssets())))
                      : List.of()));
        });
    return out;
  }

  private List<LocalObject> scenarios(Set<String> stixIds, Access access) {
    // A scenario generated from OpenCTI knowledge keeps the STIX id it came from as its external
    // reference; several scenarios may share it, the most recent one the caller can read is the
    // pivot. A scenario the caller has no grant on is never returned, not even its name.
    Map<String, Scenario> byKey = new LinkedHashMap<>();
    for (Scenario s : scenarioService.findAllByExternalReferenceIn(stixIds)) {
      if (!byKey.containsKey(s.getExternalReference()) && access.canReadScenario(s.getId())) {
        byKey.put(s.getExternalReference(), s);
      }
    }
    if (byKey.isEmpty()) {
      return List.of();
    }
    // One grouped read: how many simulations each pivot scenario ran, and when the latest started.
    Map<String, ScenarioSimulationsRow> runs = new HashMap<>();
    for (ScenarioSimulationsRow row :
        exerciseService.countSimulationsByScenarioIdIn(
            byKey.values().stream().map(Scenario::getId).toList())) {
      runs.put(row.getScenarioId(), row);
    }
    List<LocalObject> out = new ArrayList<>();
    for (Scenario s : byKey.values()) {
      ScenarioSimulationsRow row = runs.get(s.getId());
      List<Fact> facts = new ArrayList<>();
      facts.add(new Fact(FACT_SIMULATIONS, String.valueOf(row == null ? 0 : row.getSimulations())));
      if (row != null && row.getLastStart() != null) {
        facts.add(new Fact(FACT_LAST_SIMULATION, row.getLastStart().toString()));
      }
      out.add(
          new LocalObject(
              s.getExternalReference(),
              s.getId(),
              "scenario",
              s.getName(),
              link("/admin/scenarios/" + s.getId()),
              List.copyOf(facts)));
    }
    return out;
  }

  private List<LocalObject> alerts(Set<String> alertLinks, Access access) {
    // A collected alert answered an inject expectation: the pivot is that inject, in its
    // simulation or as an atomic testing. Rows come newest alert first; the first readable one per
    // link wins: an inject is read through its simulation, or as an atomic testing without one.
    Map<String, LocalObject> byKey = new LinkedHashMap<>();
    for (AlertInjectRow row :
        injectExpectationTraceService.findAlertInjectsByAlertLinkIn(alertLinks)) {
      if (byKey.containsKey(row.getAlertLink()) || !canRead(row, access)) {
        continue;
      }
      String path =
          row.getExerciseId() == null
              ? "/admin/atomic_testings/" + row.getInjectId()
              : "/admin/simulations/" + row.getExerciseId() + "/injects/" + row.getInjectId();
      byKey.putIfAbsent(
          row.getAlertLink(),
          new LocalObject(
              row.getAlertLink(), row.getInjectId(), "inject", row.getInjectTitle(), link(path)));
    }
    return new ArrayList<>(byKey.values());
  }

  private static boolean canRead(AlertInjectRow row, Access access) {
    return row.getExerciseId() == null
        ? access.canReadAtomicTesting(row.getInjectId())
        : access.canReadSimulation(row.getExerciseId());
  }

  private String link(String path) {
    String base = openAEVConfig.getBaseUrl();
    if (base == null || base.isBlank()) {
      return path;
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
  }
}
