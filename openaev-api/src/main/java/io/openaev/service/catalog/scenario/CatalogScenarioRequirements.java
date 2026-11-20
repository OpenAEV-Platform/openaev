package io.openaev.service.catalog.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest.Requires;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.Injector;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.service.InjectorService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.catalog.scenario.MissingRequirement.Status;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.utils.catalog.VersionConstraint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What a catalogue scenario needs to be installed and this platform lacks: its platform version and
 * its injectors, without which its injects have no contract. A missing executor only stops a run.
 */
@Service
@RequiredArgsConstructor
public class CatalogScenarioRequirements {

  private final InjectorService injectorService;
  private final PlatformSettingsService platformSettingsService;
  private final CatalogConnectorService catalogConnectorService;
  private final ObjectMapper mapper;

  /** Checks scenarios to install against what this platform has, read once. */
  public Checker checker() {
    return new Checker(
        mapper,
        platformSettingsService.getPlatformVersion(),
        injectorTypes(),
        integrationsBySlug());
  }

  /** Checks scenarios to show, also telling whether each missing integration is deployed. */
  public Checker checkerWithStatus() {
    return new StatusChecker(
        mapper,
        platformSettingsService.getPlatformVersion(),
        injectorTypes(),
        integrationsBySlug(),
        catalogConnectorService.deployedInstanceCounts().keySet());
  }

  private Set<String> injectorTypes() {
    return injectorService.findAll().stream().map(Injector::getType).collect(Collectors.toSet());
  }

  // An injector is enabled from the catalogue integration of the same slug, when there is one.
  private Map<String, CatalogConnector> integrationsBySlug() {
    return catalogConnectorService.findAll().stream()
        .filter(connector -> connector.getSlug() != null)
        .collect(
            Collectors.toMap(
                CatalogConnector::getSlug, Function.identity(), (first, other) -> first));
  }

  /** What each scenario lacks on this platform. */
  public static class Checker {

    private final ObjectMapper mapper;
    private final String platformVersion;
    private final Set<String> injectors;
    private final Map<String, CatalogConnector> integrationsBySlug;

    Checker(
        ObjectMapper mapper,
        String platformVersion,
        Set<String> injectors,
        Map<String, CatalogConnector> integrationsBySlug) {
      this.mapper = mapper;
      this.platformVersion = platformVersion;
      this.injectors = injectors;
      this.integrationsBySlug = integrationsBySlug;
    }

    public List<MissingRequirement> missing(CatalogScenario item) {
      Requires requires = read(item);
      List<MissingRequirement> missing = new ArrayList<>();
      if (requires == null) {
        return missing;
      }
      if (requires.openaev() != null
          && !VersionConstraint.allows(requires.openaev(), platformVersion)) {
        String label = "OpenAEV " + requires.openaev();
        missing.add(new MissingRequirement(label, label, null, null));
      }
      Optional.ofNullable(requires.injectors()).orElse(List.of()).stream()
          .filter(type -> !injectors.contains(type))
          .forEach(type -> missing.add(injector(type)));
      return missing;
    }

    /** Where the integration bringing a missing injector stands; unknown to a plain checker. */
    Optional<Status> status(String integrationId) {
      return Optional.empty();
    }

    private MissingRequirement injector(String type) {
      String label = "Injector " + type;
      Optional<CatalogConnector> integration = Optional.ofNullable(integrationsBySlug.get(type));
      String integrationId = integration.map(CatalogConnector::getId).orElse(null);
      return new MissingRequirement(
          label,
          integration
              .map(CatalogConnector::getTitle)
              .filter(title -> !title.isBlank())
              .orElse(label),
          integrationId,
          status(integrationId).orElse(null));
    }

    private Requires read(CatalogScenario item) {
      if (item.getRequires() == null) {
        return null;
      }
      try {
        return mapper.readValue(item.getRequires(), Requires.class);
      } catch (IOException e) {
        throw new IllegalStateException("Unreadable requirements", e);
      }
    }
  }

  static class StatusChecker extends Checker {

    private final Set<String> deployed;

    StatusChecker(
        ObjectMapper mapper,
        String platformVersion,
        Set<String> injectors,
        Map<String, CatalogConnector> integrationsBySlug,
        Set<String> deployed) {
      super(mapper, platformVersion, injectors, integrationsBySlug);
      this.deployed = deployed;
    }

    @Override
    Optional<Status> status(String integrationId) {
      return Optional.of(Status.of(integrationId != null && deployed.contains(integrationId)));
    }
  }
}
