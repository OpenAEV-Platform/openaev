package io.openaev.service.autonomous;

import io.openaev.api.autonomous.dto.CapabilityQueryInput;
import io.openaev.api.autonomous.dto.CapabilityReport;
import io.openaev.api.autonomous.dto.CapabilityResolution;
import io.openaev.api.autonomous.dto.CapabilityResolution.ResolvedContract;
import io.openaev.api.autonomous.dto.CapabilityResolution.SuggestedConnector;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.service.PreviewFeatureService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Answers "can this platform do X, and if not, what should be installed?" for the autonomous
 * attack-path feature. It maps a requested technique (MITRE ATT&amp;CK external id) or desired
 * output/primitive type ({@link ContractOutputType}) onto the installed injector contracts that
 * satisfy it - {@code attackPatterns} for techniques, {@code getProviding()} for output types -
 * and, when nothing satisfies it, suggests marketplace connectors ({@link CatalogConnector}) an
 * operator could install to close the gap.
 *
 * <p>This is the substrate for two things: the operator UI's capability-gap strip on the run
 * creation / live view, and the orchestrator's {@code openaev_capability_gaps} MCP tool - so the AI
 * brain can decide, before attempting a technique, whether to execute it now, narrate a capability
 * gap, or (when authorized) craft a custom arsenal item on the fly.
 *
 * <p>Deliberately built against the typed output model (the primitive/complex type registry the
 * chaining engine is moving to, chaining issues #6536 / #6198), not against ad-hoc contract content
 * fields, so it stays correct as the engine's type model lands.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CapabilityResolverService {

  private final InjectorContractRepository injectorContractRepository;
  private final CatalogConnectorRepository catalogConnectorRepository;
  private final PreviewFeatureService previewFeatureService;

  /**
   * Keyword hints per capability, so a marketplace suggestion for a gap like "credentials" also
   * surfaces phishing / dumping connectors whose title never literally says "credentials". Best
   * effort only; the resolver still keyword-matches the raw token and label on top of these.
   */
  private static final Map<String, List<String>> CAPABILITY_KEYWORDS =
      Map.ofEntries(
          Map.entry("credentials", List.of("credential", "password", "phishing", "dump", "secret")),
          Map.entry("admin_username", List.of("credential", "privilege", "admin")),
          Map.entry("username", List.of("credential", "account", "enumeration")),
          Map.entry("port", List.of("scan", "port", "nmap", "discovery")),
          Map.entry("portscan", List.of("scan", "port", "nmap", "discovery")),
          Map.entry("cve", List.of("vulnerability", "scanner", "nuclei", "cve", "exploit")),
          Map.entry("vulnerability", List.of("vulnerability", "scanner", "nuclei", "exploit")),
          Map.entry("share", List.of("smb", "share", "file", "enumeration")),
          Map.entry("kerberoastable_account", List.of("kerberos", "active directory", "ad")),
          Map.entry("asreproastable_account", List.of("kerberos", "active directory", "ad")),
          Map.entry("password_policy", List.of("active directory", "ad", "policy")),
          Map.entry("computer", List.of("active directory", "ad", "enumeration")),
          Map.entry("group", List.of("active directory", "ad", "enumeration")),
          Map.entry("sid", List.of("active directory", "ad")),
          Map.entry("file", List.of("file", "exfil", "collection")),
          Map.entry("delegation", List.of("active directory", "ad", "delegation")));

  private static final int MAX_SUGGESTIONS = 5;

  private void requireFeature() {
    if (!previewFeatureService.isAutonomousAttackPathEnabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  /**
   * Resolves every technique and output type in the query against the installed threat arsenal.
   * Loads the tenant's contracts once and builds in-memory indexes; this is an on-demand advisory
   * call (run creation, capability-gap check), never a per-step hot path, so a single enumeration
   * is acceptable.
   */
  @Transactional(readOnly = true)
  public CapabilityReport resolve(CapabilityQueryInput input) {
    requireFeature();

    ContractIndex index = buildIndex();
    Set<Endpoint.PLATFORM_TYPE> platformFilter = parsePlatforms(input.getPlatforms());

    List<CapabilityResolution> resolutions = new ArrayList<>();
    for (String technique : nonNull(input.getTechniques())) {
      String token = technique.trim();
      if (!token.isEmpty()) {
        resolutions.add(resolveTechnique(token, index, platformFilter));
      }
    }
    for (String outputType : nonNull(input.getOutputTypes())) {
      String token = outputType.trim();
      if (!token.isEmpty()) {
        resolutions.add(resolveOutputType(token, index, platformFilter));
      }
    }

    List<CapabilityResolution> gaps =
        resolutions.stream().filter(r -> !r.isSatisfied()).collect(Collectors.toList());
    return new CapabilityReport(resolutions, gaps, gaps.isEmpty() && !resolutions.isEmpty());
  }

  private CapabilityResolution resolveTechnique(
      String externalId, ContractIndex index, Set<Endpoint.PLATFORM_TYPE> platformFilter) {
    String key = externalId.toUpperCase(Locale.ROOT);
    List<ResolvedContract> matches =
        applyPlatformFilter(index.byTechnique.getOrDefault(key, List.of()), platformFilter);
    String label = index.techniqueNames.getOrDefault(key, externalId);
    return finalize(CapabilityResolution.Kind.TECHNIQUE, externalId, label, matches);
  }

  private CapabilityResolution resolveOutputType(
      String label, ContractIndex index, Set<Endpoint.PLATFORM_TYPE> platformFilter) {
    String key = label.toLowerCase(Locale.ROOT);
    List<ResolvedContract> matches =
        applyPlatformFilter(index.byOutputType.getOrDefault(key, List.of()), platformFilter);
    return finalize(CapabilityResolution.Kind.OUTPUT_TYPE, key, key, matches);
  }

  private CapabilityResolution finalize(
      CapabilityResolution.Kind kind, String token, String label, List<ResolvedContract> matches) {
    boolean satisfied = !matches.isEmpty();
    List<SuggestedConnector> suggestions = satisfied ? List.of() : suggestConnectors(token, label);
    return new CapabilityResolution(kind, token, label, satisfied, matches, suggestions);
  }

  // region indexing

  private ContractIndex buildIndex() {
    ContractIndex index = new ContractIndex();
    for (InjectorContract contract : injectorContractRepository.findAll()) {
      ResolvedContract resolved = toResolved(contract);
      for (AttackPattern pattern : contract.getAttackPatterns()) {
        String extId = pattern.getExternalId();
        if (extId == null || extId.isBlank()) {
          continue;
        }
        String key = extId.toUpperCase(Locale.ROOT);
        index.byTechnique.computeIfAbsent(key, k -> new ArrayList<>()).add(resolved);
        index.techniqueNames.putIfAbsent(
            key, pattern.getName() != null ? pattern.getName() : extId);
      }
      for (ContractOutputType type : contract.getProviding()) {
        index
            .byOutputType
            .computeIfAbsent(type.getLabel().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
            .add(resolved);
      }
    }
    return index;
  }

  private ResolvedContract toResolved(InjectorContract contract) {
    List<String> platforms =
        contract.getPlatforms() == null
            ? List.of()
            : Arrays.stream(contract.getPlatforms())
                .filter(Objects::nonNull)
                .map(Enum::name)
                .collect(Collectors.toList());
    String label =
        contract.getLabels() != null
            ? contract
                .getLabels()
                .getOrDefault(
                    "en",
                    contract.getLabels().values().stream().findFirst().orElse(contract.getId()))
            : contract.getId();
    return new ResolvedContract(contract.getId(), label, contract.getInjectorType(), platforms);
  }

  // endregion

  // region marketplace suggestions

  /**
   * Suggests marketplace connectors that could close a capability gap. Matches the token, its
   * label, and any curated keyword hints against each connector's title, description and use cases.
   * Purely advisory: it never installs anything, it hands the operator the links to do so.
   */
  private List<SuggestedConnector> suggestConnectors(String token, String label) {
    List<String> needles = new ArrayList<>();
    needles.add(token.toLowerCase(Locale.ROOT));
    if (label != null) {
      needles.add(label.toLowerCase(Locale.ROOT));
    }
    needles.addAll(CAPABILITY_KEYWORDS.getOrDefault(token.toLowerCase(Locale.ROOT), List.of()));

    List<SuggestedConnector> matches = new ArrayList<>();
    for (CatalogConnector connector : catalogConnectorRepository.findAll()) {
      if (connector.getDeletedAt() != null) {
        continue;
      }
      if (matchesConnector(connector, needles)) {
        matches.add(
            new SuggestedConnector(
                connector.getId(),
                connector.getTitle(),
                connector.getSlug(),
                connector.getShortDescription(),
                connector.getLogoUrl(),
                connector.getSubscriptionLink(),
                connector.getSourceCode()));
      }
      if (matches.size() >= MAX_SUGGESTIONS) {
        break;
      }
    }
    return matches;
  }

  private boolean matchesConnector(CatalogConnector connector, List<String> needles) {
    StringBuilder haystack = new StringBuilder();
    append(haystack, connector.getTitle());
    append(haystack, connector.getShortDescription());
    append(haystack, connector.getDescription());
    if (connector.getUseCases() != null) {
      connector.getUseCases().forEach(useCase -> append(haystack, useCase));
    }
    String text = haystack.toString();
    return needles.stream().anyMatch(needle -> !needle.isBlank() && text.contains(needle));
  }

  private static void append(StringBuilder builder, String value) {
    if (value != null) {
      builder.append(value.toLowerCase(Locale.ROOT)).append(' ');
    }
  }

  // endregion

  // region helpers

  private Set<Endpoint.PLATFORM_TYPE> parsePlatforms(List<String> platforms) {
    if (platforms == null || platforms.isEmpty()) {
      return Set.of();
    }
    return platforms.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(p -> !p.isEmpty())
        .map(this::parsePlatform)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private Endpoint.PLATFORM_TYPE parsePlatform(String value) {
    try {
      return Endpoint.PLATFORM_TYPE.valueOf(value);
    } catch (IllegalArgumentException e) {
      log.debug("[Autonomous] Ignoring unknown platform filter value '{}'", value);
      return null;
    }
  }

  private List<ResolvedContract> applyPlatformFilter(
      List<ResolvedContract> contracts, Set<Endpoint.PLATFORM_TYPE> platformFilter) {
    if (platformFilter.isEmpty()) {
      return contracts;
    }
    Set<String> wanted = platformFilter.stream().map(Enum::name).collect(Collectors.toSet());
    return contracts.stream()
        .filter(
            c ->
                c.getPlatforms() == null
                    || c.getPlatforms().isEmpty()
                    || c.getPlatforms().stream().anyMatch(wanted::contains))
        .collect(Collectors.toList());
  }

  private static <T> List<T> nonNull(List<T> list) {
    return list == null ? List.of() : list;
  }

  /** In-memory indexes over the tenant's installed contracts, built once per resolve call. */
  private static final class ContractIndex {
    private final Map<String, List<ResolvedContract>> byTechnique = new LinkedHashMap<>();
    private final Map<String, List<ResolvedContract>> byOutputType = new LinkedHashMap<>();
    private final Map<String, String> techniqueNames = new LinkedHashMap<>();
  }

  // endregion
}
