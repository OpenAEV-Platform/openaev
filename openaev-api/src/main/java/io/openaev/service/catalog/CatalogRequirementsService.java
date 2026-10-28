package io.openaev.service.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.CatalogManifest.Requires;
import io.openaev.api.catalog.CatalogMissingRequirement;
import io.openaev.api.catalog.CatalogMissingRequirement.Kind;
import io.openaev.api.catalog.CatalogMissingRequirement.Status;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogItem;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.Executor;
import io.openaev.database.model.Injector;
import io.openaev.executors.ExecutorService;
import io.openaev.service.InjectorService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a catalogue item needs and this platform lacks: shown before install, refused at it. */
@Service
@RequiredArgsConstructor
public class CatalogRequirementsService {

  // The steps of a chained scenario are not bound to the release yet: their payloads would escape
  // its checks and revocations, and an update would not write them.
  public static final String CHAINED_NOT_SUPPORTED = "Chained scenarios are not supported yet";
  private static final String CHAINED = "chained";

  private static final Pattern CONSTRAINT = Pattern.compile("^(>=|>|==|~=)([0-9]+(\\.[0-9]+)*)$");

  private final InjectorService injectorService;
  private final ExecutorService executorService;
  private final LicenseCacheManager licenseCacheManager;
  private final PlatformSettingsService platformSettingsService;
  private final CatalogConnectorService catalogConnectorService;
  private final ObjectMapper mapper;

  /** One checker per request: the platform's injectors and executors are read once. */
  @Transactional(readOnly = true)
  public Checker checker() {
    Set<String> injectors =
        injectorService.findAll().stream().map(Injector::getType).collect(Collectors.toSet());
    Set<String> executors =
        StreamSupport.stream(executorService.executors().spliterator(), false)
            .map(Executor::getType)
            .collect(Collectors.toSet());
    return new Checker(
        injectors,
        executors,
        licenseCacheManager.isEnterpriseEditionActive(),
        platformSettingsService.getPlatformVersion());
  }

  public class Checker {
    private final Set<String> injectors;
    private final Set<String> executors;
    private final boolean enterprise;
    private final String version;

    Checker(Set<String> injectors, Set<String> executors, boolean enterprise, String version) {
      this.injectors = injectors;
      this.executors = executors;
      this.enterprise = enterprise;
      this.version = version;
    }

    public List<String> missing(CatalogItem item) {
      return missingPrerequisites(item).stream().map(CatalogMissingRequirement::label).toList();
    }

    public List<CatalogMissingRequirement> missingPrerequisites(CatalogItem item) {
      List<CatalogMissingRequirement> missing = new ArrayList<>();
      if (isChained(item)) {
        missing.add(other(Kind.CHAINING, CHAINED, CHAINED_NOT_SUPPORTED));
      }
      Requires requires = read(item);
      if (requires == null) {
        return missing;
      }
      if (requires.openaev() != null && !versionMatches(requires.openaev(), version)) {
        missing.add(other(Kind.OPENAEV, requires.openaev(), "OpenAEV " + requires.openaev()));
      }
      if ("EE".equalsIgnoreCase(requires.edition()) && !enterprise) {
        missing.add(other(Kind.EDITION, "EE", "Enterprise Edition"));
      }
      Optional.ofNullable(requires.injectors()).orElse(List.of()).stream()
          .filter(type -> !injectors.contains(type))
          .forEach(type -> missing.add(injector(type)));
      Optional.ofNullable(requires.executors()).orElse(List.of()).stream()
          .filter(type -> !executors.contains(type))
          .forEach(type -> missing.add(other(Kind.EXECUTOR, type, "Executor " + type)));
      return missing;
    }

    // An injector not registered yet is deployed from its catalogue connector, when one exists.
    private CatalogMissingRequirement injector(String type) {
      Optional<CatalogConnector> connector = catalogConnectorService.findBySlug(type);
      return new CatalogMissingRequirement(
          Kind.INJECTOR,
          type,
          "Injector " + type,
          connector.map(CatalogConnector::getId).orElse(null),
          connector.map(CatalogConnector::getTitle).orElse(null),
          connector
              .map(c -> c.getInstances().isEmpty() ? Status.NOT_DEPLOYED : Status.NOT_RUNNING)
              .orElse(Status.NOT_DEPLOYED));
    }

    private static CatalogMissingRequirement other(Kind kind, String key, String label) {
      return new CatalogMissingRequirement(kind, key, label, null, null, null);
    }
  }

  public static boolean isChained(CatalogItem item) {
    return item.getType() == ContentType.SCENARIO
        && CHAINED.equalsIgnoreCase(item.getScenarioType());
  }

  private Requires read(CatalogItem item) {
    if (item.getRequires() == null) {
      return null;
    }
    try {
      return mapper.readValue(item.getRequires(), Requires.class);
    } catch (Exception e) {
      throw new IllegalStateException("Unreadable requirements", e);
    }
  }

  // An unreadable constraint or platform version never blocks: the repository CI checks the form.
  static boolean versionMatches(String constraint, String platformVersion) {
    Matcher matcher = CONSTRAINT.matcher(constraint.trim());
    List<Long> current = numbers(platformVersion);
    if (!matcher.matches() || current.isEmpty()) {
      return true;
    }
    List<Long> wanted = numbers(matcher.group(2));
    int order = compare(current, wanted);
    return switch (matcher.group(1)) {
      case ">=" -> order >= 0;
      case ">" -> order > 0;
      case "==" -> order == 0;
      // Compatible release: same leading parts, then at least the wanted version.
      case "~=" ->
          order >= 0
              && compare(
                      current.subList(0, Math.min(current.size(), wanted.size() - 1)),
                      wanted.subList(0, wanted.size() - 1))
                  == 0;
      default -> true;
    };
  }

  private static List<Long> numbers(String version) {
    if (version == null) {
      return List.of();
    }
    Matcher matcher = Pattern.compile("^([0-9]+(\\.[0-9]+)*)").matcher(version.trim());
    if (!matcher.find()) {
      return List.of();
    }
    return Arrays.stream(matcher.group(1).split("\\.")).map(Long::parseLong).toList();
  }

  private static int compare(List<Long> a, List<Long> b) {
    for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
      long x = i < a.size() ? a.get(i) : 0;
      long y = i < b.size() ? b.get(i) : 0;
      if (x != y) {
        return Long.compare(x, y);
      }
    }
    return 0;
  }
}
