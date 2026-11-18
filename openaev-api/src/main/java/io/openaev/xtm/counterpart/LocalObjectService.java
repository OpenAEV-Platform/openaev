package io.openaev.xtm.counterpart;

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
import java.util.ArrayList;
import java.util.Collection;
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
 * sees, nothing more. Attack patterns and vulnerabilities are tenant-active tables: they are read
 * through their reviewed services (TenantActiveTableAccessArchTest), never their repositories.
 */
@Service
@RequiredArgsConstructor
public class LocalObjectService {

  /** One object of this OpenAEV matching a key. */
  public record LocalObject(String key, String id, String type, String name, String url) {}

  private final OpenAEVConfig openAEVConfig;
  private final AssetRepository assetRepository;
  private final AttackPatternService attackPatternService;
  private final VulnerabilityService vulnerabilityService;
  private final ScenarioRepository scenarioRepository;
  private final InjectExpectationTraceRepository injectExpectationTraceRepository;

  public List<LocalObject> find(CounterpartKind kind, Collection<String> keys) {
    Set<String> normalised = normalise(kind, keys);
    if (normalised.isEmpty()) {
      return List.of();
    }
    return switch (kind) {
      case ASSET -> assets(normalised);
      case ATTACK_PATTERN -> attackPatterns(normalised);
      case VULNERABILITY -> vulnerabilities(normalised);
      case THREAT -> scenarios(normalised);
      case ALERT -> alerts(normalised);
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

  private List<LocalObject> assets(Set<String> hostnames) {
    // Hostnames are stored lower-case (Asset#setHostname), the ASSET kind normalises the same way.
    Map<String, LocalObject> byKey = new LinkedHashMap<>();
    for (Asset asset : assetRepository.findAllByHostnameIn(hostnames)) {
      byKey.putIfAbsent(
          asset.getHostname(),
          new LocalObject(
              asset.getHostname(),
              asset.getId(),
              "asset",
              asset.getName(),
              link("/admin/assets/" + asset.getId())));
    }
    return new ArrayList<>(byKey.values());
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

  private List<LocalObject> vulnerabilities(Set<String> cves) {
    Map<String, LocalObject> byKey = new LinkedHashMap<>();
    for (Vulnerability v : vulnerabilityService.getVulnerabilitiesByExternalIds(cves)) {
      String key = v.getExternalId().toUpperCase(Locale.ROOT);
      byKey.putIfAbsent(
          key,
          new LocalObject(
              key,
              v.getId(),
              "vulnerability",
              v.getExternalId(),
              link("/admin/settings/taxonomies/vulnerabilities")));
    }
    return new ArrayList<>(byKey.values());
  }

  private List<LocalObject> scenarios(Set<String> stixIds) {
    // A scenario generated from OpenCTI knowledge keeps the STIX id it came from as its external
    // reference; several scenarios may share it, the first (most recent) one is the pivot.
    Map<String, LocalObject> byKey = new LinkedHashMap<>();
    for (Scenario s :
        scenarioRepository.findAllByExternalReferenceInOrderByUpdatedAtDesc(stixIds)) {
      byKey.putIfAbsent(
          s.getExternalReference(),
          new LocalObject(
              s.getExternalReference(),
              s.getId(),
              "scenario",
              s.getName(),
              link("/admin/scenarios/" + s.getId())));
    }
    return new ArrayList<>(byKey.values());
  }

  private List<LocalObject> alerts(Set<String> alertLinks) {
    // A collected alert answered an inject expectation: the pivot is that inject, in its
    // simulation or as an atomic testing. Rows come newest alert first; the first per link wins.
    Map<String, LocalObject> byKey = new LinkedHashMap<>();
    for (AlertInjectRow row :
        injectExpectationTraceRepository.findAlertInjectsByAlertLinkIn(alertLinks)) {
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

  private String link(String path) {
    String base = openAEVConfig.getBaseUrl();
    if (base == null || base.isBlank()) {
      return path;
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
  }
}
