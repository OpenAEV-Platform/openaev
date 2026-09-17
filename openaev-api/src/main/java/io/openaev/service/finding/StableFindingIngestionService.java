package io.openaev.service.finding;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingAggregationCategory;
import io.openaev.database.model.FindingEvidenceScope;
import io.openaev.database.model.FindingLocationType;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.FindingTargetRole;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.StableFinding;
import io.openaev.database.model.StableFindingCategory;
import io.openaev.database.model.StableFindingLifecycle;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingOccurrenceRepository;
import io.openaev.database.repository.StableFindingRepository;
import io.openaev.rest.inject.service.ContractOutputContext;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists the stable-finding projection while the legacy finding remains the rollback source. */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class StableFindingIngestionService {

  private static final String UNKNOWN_PRODUCT = "unknown-product";
  private static final Set<ContractOutputType> INFORMATIVE_TYPES =
      EnumSet.of(
          ContractOutputType.Text,
          ContractOutputType.ActionOutput,
          ContractOutputType.Number,
          ContractOutputType.Port,
          ContractOutputType.IPv4,
          ContractOutputType.IPv6,
          ContractOutputType.Email);

  private final StableFindingRepository stableFindingRepository;
  private final FindingOccurrenceRepository findingOccurrenceRepository;

  /** Dual-writes injector callback findings and their raw source records. */
  public void ingest(
      @NotNull Inject inject,
      @NotNull ContractOutputContext output,
      @NotNull List<Finding> findings,
      @NotNull List<JsonNode> sourceRecords) {
    if (findings.size() != sourceRecords.size()) {
      throw new IllegalArgumentException("A source record is required for every generated finding");
    }
    for (int index = 0; index < findings.size(); index++) {
      ingestFinding(inject, output, findings.get(index), sourceRecords.get(index));
    }
  }

  /** Dual-writes a finding produced by the agent callback path. */
  public void ingestAgent(
      @NotNull Inject inject,
      @NotNull ContractOutputContext output,
      @NotNull String value,
      @NotNull Asset asset,
      @NotNull JsonNode sourceRecord) {
    Finding finding = new Finding();
    finding.setField(output.key());
    finding.setName(output.name());
    finding.setType(output.type());
    finding.setValue(value);
    finding.setAssets(List.of(asset));
    finding.setInject(inject);
    finding.setTenant(inject.getTenant());
    ingestFinding(inject, output, finding, sourceRecord);
  }

  private void ingestFinding(
      Inject inject, ContractOutputContext output, Finding finding, JsonNode source) {
    String tenantId =
        Objects.requireNonNull(inject.getTenant(), "Inject tenant is required").getId();
    String namespace = sourceNamespace(inject, output, source);
    Instant observedAt = observedAt(source, finding);
    StableFindingLifecycle lifecycle = lifecycle(source);
    String key = stableKey(tenantId, namespace, output.type(), finding.getValue());
    StableFinding stable =
        stableFindingRepository
            .findByKeyAndTenantId(key, tenantId)
            .orElseGet(
                () ->
                    createStableFinding(
                        inject, output, finding, namespace, key, observedAt, lifecycle));
    mergeTags(stable, finding);

    List<Location> locations = locations(finding, source);
    for (Location location : locations) {
      if (findingOccurrenceRepository.existsLiveOccurrence(
          tenantId, stable.getId(), inject.getId(), location.type(), location.key())) {
        continue;
      }
      findingOccurrenceRepository.save(
          occurrence(stable, inject, finding, source, location, observedAt));
      refreshStable(stable, tenantId);
    }
  }

  private StableFinding createStableFinding(
      Inject inject,
      ContractOutputContext output,
      Finding finding,
      String namespace,
      String key,
      Instant observedAt,
      StableFindingLifecycle lifecycle) {
    StableFinding created = new StableFinding();
    created.setKey(key);
    created.setSourceNamespace(namespace);
    created.setContractOutputKey(output.key());
    created.setSourceInjector(sourceInjector(inject));
    created.setType(output.type());
    created.setValue(finding.getValue());
    created.setCategory(category(output.type()));
    created.setAggregationCategory(FindingAggregationCategory.from(output.type()));
    created.setFirstSeen(observedAt);
    created.setLastSeen(observedAt);
    created.setLifecycle(lifecycle);
    created.setTenant(inject.getTenant());
    if (finding.getTags() != null) {
      created.setTags(new LinkedHashSet<>(finding.getTags()));
    }
    return stableFindingRepository.save(created);
  }

  private void mergeTags(StableFinding stable, Finding finding) {
    if (finding.getTags() == null || finding.getTags().isEmpty()) {
      return;
    }
    if (stable.getTags().addAll(finding.getTags())) {
      stableFindingRepository.save(stable);
    }
  }

  private void refreshStable(StableFinding stable, String tenantId) {
    List<FindingOccurrence> occurrences =
        findingOccurrenceRepository.findAllByStableFindingIdAndTenantId(stable.getId(), tenantId);
    if (occurrences.isEmpty()) {
      return;
    }
    stable.setFirstSeen(
        occurrences.stream()
            .map(FindingOccurrence::getObservedAt)
            .min(Instant::compareTo)
            .orElseThrow());
    stable.setLastSeen(
        occurrences.stream()
            .map(FindingOccurrence::getObservedAt)
            .max(Instant::compareTo)
            .orElseThrow());
    findingOccurrenceRepository
        .findFirstByStableFindingIdAndTenantIdOrderByObservedAtDescInjectIdDescLocationTypeDescLocationKeyDescIdDesc(
            stable.getId(), tenantId)
        .ifPresent(latest -> stable.setLifecycle(lifecycle(latest.getOutcome())));
    stableFindingRepository.save(stable);
  }

  private FindingOccurrence occurrence(
      StableFinding stable,
      Inject inject,
      Finding finding,
      JsonNode source,
      Location location,
      Instant observedAt) {
    FindingOccurrence occurrence = new FindingOccurrence();
    occurrence.setStableFinding(stable);
    occurrence.setMigratedFrom(finding);
    occurrence.setInject(inject);
    occurrence.setTenant(inject.getTenant());
    occurrence.setObservedAt(observedAt);
    occurrence.setOutcome(text(source, "status_code"));
    occurrence.setEvidenceDetail(
        firstNonBlank(text(source, "status_detail"), text(source, "message")));
    occurrence.setStatusDetail(
        firstNonBlank(text(source, "status_detail"), text(source, "message")));
    occurrence.setRawPayload(source.toString());
    occurrence.setObservedSeverity(firstNonBlank(text(source, "severity"), finding.getSeverity()));
    occurrence.setObservedSeverityId(integer(source, "severity_id"));
    occurrence.setSourceFindingUid(text(source, "finding_info", "uid"));
    occurrence.setTitle(firstNonBlank(text(source, "finding_info", "title"), finding.getName()));
    occurrence.setDescription(text(source, "finding_info", "desc"));
    occurrence.setRisk(
        firstNonBlank(
            text(source, "risk_details", "description"), nodeText(source.get("risk_details"))));
    occurrence.setCategories(categories(source, outputLabels(finding)));
    occurrence.setAttackPatterns(attackPatterns(inject, source));
    occurrence.setRemediation(finding.getRemediation());
    occurrence.setCompliance(finding.getCompliance());
    occurrence.setScanId(firstNonBlank(text(source, "metadata", "uid"), text(source, "scan_id")));
    occurrence.setLocationType(location.type());
    occurrence.setLocationKey(location.key());
    occurrence.setLocation(location.display());
    occurrence.setLocationAsset(location.asset());
    occurrence.setLocationUser(location.user());
    occurrence.setLocationTeam(location.team());
    occurrence.setAssets(new ArrayList<>(nullSafe(finding.getAssets())));
    occurrence.setUsers(new ArrayList<>(nullSafe(finding.getUsers())));
    occurrence.setTeams(new ArrayList<>(nullSafe(finding.getTeams())));
    occurrence.setTargetRole(targetRole(finding, source, location));
    occurrence.setEvidenceScope(evidenceScope(finding, source));
    occurrence.setResource(firstNonBlank(location.resourceId(), finding.getResource()));
    occurrence.setResourceSnapshot(location.snapshot());
    occurrence.setResourceProvider(location.provider());
    occurrence.setResourceAccount(location.account());
    occurrence.setResourceRegion(location.region());
    occurrence.setResourceName(location.resourceName());
    occurrence.setResourceTypeSnapshot(location.resourceType());
    occurrence.setResourceService(location.groupName());
    return occurrence;
  }

  private List<Location> locations(Finding finding, JsonNode source) {
    if (finding.getType() == ContractOutputType.OCSF) {
      JsonNode resources = source.get("resources");
      if (resources != null && resources.isArray() && !resources.isEmpty()) {
        List<Location> result = new ArrayList<>();
        for (JsonNode resource : resources) {
          String identifier =
              firstNonBlank(text(resource, "data", "metadata", "arn"), text(resource, "uid"));
          String key =
              firstNonBlank(
                  identifier,
                  sha256("", "resource", ContractOutputType.OCSF.name(), resource.toString()));
          result.add(
              new Location(
                  FindingLocationType.RESOURCE,
                  normalize(key),
                  identifier,
                  null,
                  null,
                  null,
                  identifier,
                  resource.toString(),
                  normalized(text(resource, "cloud_partition")),
                  firstNonBlank(
                      text(resource, "account", "uid"), text(source, "cloud", "account", "uid")),
                  firstNonBlank(text(resource, "region"), text(source, "cloud", "region")),
                  text(resource, "name"),
                  text(resource, "type"),
                  text(resource, "group", "name")));
        }
        return result;
      }
    }
    List<Location> result = new ArrayList<>();
    nullSafe(finding.getAssets())
        .forEach(
            asset ->
                result.add(
                    new Location(
                        FindingLocationType.ASSET,
                        asset.getId(),
                        asset.getName(),
                        asset,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
    nullSafe(finding.getUsers())
        .forEach(
            user ->
                result.add(
                    new Location(
                        FindingLocationType.USER,
                        user.getId(),
                        user.getName(),
                        null,
                        user,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
    nullSafe(finding.getTeams())
        .forEach(
            team ->
                result.add(
                    new Location(
                        FindingLocationType.TEAM,
                        team.getId(),
                        team.getName(),
                        null,
                        null,
                        team,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
    return result.isEmpty()
        ? List.of(
            new Location(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null))
        : result;
  }

  private String sourceNamespace(Inject inject, ContractOutputContext output, JsonNode source) {
    Injector injector = sourceInjector(inject);
    String injectorId = injector == null ? "legacy-inject-" + inject.getId() : injector.getId();
    String namespace = injectorId + "/" + output.key();
    if (output.type() == ContractOutputType.OCSF) {
      namespace +=
          "/"
              + normalized(
                  firstNonBlank(
                      text(source, "metadata", "product", "uid"),
                      text(source, "metadata", "product", "name"),
                      UNKNOWN_PRODUCT));
    }
    return namespace;
  }

  private Injector sourceInjector(Inject inject) {
    if (inject.getInjector() != null) {
      return inject.getInjector();
    }
    return inject.getInjectorContract().map(contract -> contract.getFirstInjector()).orElse(null);
  }

  static String stableKey(
      String tenantId, String namespace, ContractOutputType type, String value) {
    return sha256(tenantId, namespace, type.name(), value);
  }

  private static String sha256(String... components) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String component : components) {
        byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
      }

      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  static StableFindingCategory category(ContractOutputType type) {
    return INFORMATIVE_TYPES.contains(type)
        ? StableFindingCategory.INFORMATIVE
        : StableFindingCategory.LOCALIZED;
  }

  private Instant observedAt(JsonNode source, Finding finding) {
    String timeDt = text(source, "time_dt");
    if (timeDt != null) {
      try {
        return OffsetDateTime.parse(timeDt).toInstant();
      } catch (DateTimeParseException exception) {
        throw new IllegalArgumentException("Invalid OCSF time_dt: " + timeDt, exception);
      }
    }
    JsonNode epoch = source.get("time");
    if (epoch != null && !epoch.isNull()) {
      double value = epoch.asDouble();
      if (Math.abs(value) >= 100_000_000_000D) {
        return Instant.ofEpochMilli((long) value);
      }
      long seconds = (long) value;
      return Instant.ofEpochSecond(seconds, (long) ((value - seconds) * 1_000_000_000L));
    }
    return finding.getCreationDate() == null ? Instant.now() : finding.getCreationDate();
  }

  private StableFindingLifecycle lifecycle(JsonNode source) {
    return lifecycle(text(source, "status_code"));
  }

  private StableFindingLifecycle lifecycle(String outcome) {
    if (outcome == null) {
      return StableFindingLifecycle.ACTIVE;
    }
    return switch (outcome.toUpperCase(Locale.ROOT)) {
      case "MANUAL" -> StableFindingLifecycle.REVIEW_REQUIRED;
      case "MUTED" -> StableFindingLifecycle.MUTED;
      default -> StableFindingLifecycle.ACTIVE;
    };
  }

  private String[] categories(JsonNode source, String[] labels) {
    Set<String> values = new LinkedHashSet<>();
    if (labels != null) {
      for (String label : labels) {
        addNonBlank(values, label);
      }
    }
    addStructuredValues(values, source.get("category_name"));
    addStructuredValues(values, source.path("finding_info").get("types"));
    addStructuredValues(values, source.path("unmapped").get("categories"));
    addNonBlank(values, text(source, "class_name"));
    addNonBlank(values, text(source, "activity_name"));
    return values.toArray(String[]::new);
  }

  private String[] outputLabels(Finding finding) {
    return finding.getLabels();
  }

  private String[] attackPatterns(Inject inject, JsonNode source) {
    Set<String> values = new LinkedHashSet<>();
    inject.getAttackPatterns().forEach(pattern -> addNonBlank(values, pattern.getExternalId()));
    JsonNode mitre = source.path("unmapped").path("compliance").get("MITRE-ATTACK");
    addStructuredValues(values, mitre);
    addStructuredValues(values, source.get("mitre_attack"));
    return values.toArray(String[]::new);
  }

  private FindingTargetRole targetRole(Finding finding, JsonNode source, Location location) {
    String host = host(source);
    List<Asset> assets = nullSafe(finding.getAssets());
    if (host == null) {
      return FindingTargetRole.EXECUTOR;
    }
    return location.type() == FindingLocationType.ASSET
            && location.asset() != null
            && hostMatchesAsset(host, location.asset())
        ? FindingTargetRole.TARGET
        : FindingTargetRole.EXECUTOR;
  }

  private FindingEvidenceScope evidenceScope(Finding finding, JsonNode source) {
    return host(source) != null && nullSafe(finding.getAssets()).size() > 1
        ? FindingEvidenceScope.GROUP
        : FindingEvidenceScope.INDIVIDUAL;
  }

  private String host(JsonNode source) {
    return normalizedHost(text(source, "host"));
  }

  private boolean hostMatchesAsset(String host, Asset asset) {
    if (host.equals(normalizedHost(asset.getHostname()))
        || host.equals(normalizedHost(asset.getName()))
        || host.equals(normalizedHost(asset.getSeenIp()))
        || host.equals(normalizedHost(asset.getUrl()))) {
      return true;
    }
    String[] ips = asset.getIps();
    if (ips != null) {
      for (String ip : ips) {
        if (host.equals(normalizedHost(ip))) {
          return true;
        }
      }
    }
    return false;
  }

  private String normalizedHost(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value
        .trim()
        .toLowerCase(Locale.ROOT)
        .replaceFirst("^[a-z][a-z0-9+.-]*://", "")
        .replaceFirst("[/?#].*$", "")
        .replaceFirst(":\\d+$", "");
  }

  private static String text(JsonNode source, String... path) {
    JsonNode value = source;
    for (String element : path) {
      if (value == null) {
        return null;
      }
      value = value.get(element);
    }
    return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
  }

  private static Integer integer(JsonNode source, String field) {
    JsonNode value = source.get(field);
    return value == null || !value.canConvertToInt() ? null : value.intValue();
  }

  private static String nodeText(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    return value.isValueNode() ? value.asText() : value.toString();
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static String normalized(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static String normalize(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static void addNonBlank(Set<String> values, String value) {
    if (value != null && !value.isBlank()) {
      values.add(value);
    }
  }

  private static void addStructuredValues(Set<String> values, JsonNode node) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isArray()) {
      node.forEach(value -> addStructuredValues(values, value));
      return;
    }
    if (node.isValueNode()) {
      addNonBlank(values, node.asText());
      return;
    }
    boolean recognized = false;
    for (String field : List.of("uid", "id", "technique_uid", "technique_id", "name", "value")) {
      JsonNode value = node.get(field);
      if (value != null) {
        recognized = true;
        addStructuredValues(values, value);
      }
    }
    if (!recognized) {
      node.elements().forEachRemaining(value -> addStructuredValues(values, value));
    }
  }

  private static <T> List<T> nullSafe(List<T> values) {
    return values == null ? List.of() : values;
  }

  private record Location(
      FindingLocationType type,
      String key,
      String display,
      Asset asset,
      User user,
      Team team,
      String resourceId,
      String snapshot,
      String provider,
      String account,
      String region,
      String resourceName,
      String resourceType,
      String groupName) {}
}
