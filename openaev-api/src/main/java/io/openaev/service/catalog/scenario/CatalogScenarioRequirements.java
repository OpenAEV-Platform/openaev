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
    return new Checker(integrationId -> null);
  }

  /** Checks scenarios to list, also telling whether each missing integration is deployed. */
  public Checker checkerWithStatus() {
    Set<String> deployed = catalogConnectorService.deployedInstanceCounts().keySet();
    return new Checker(
        integrationId -> Status.of(integrationId != null && deployed.contains(integrationId)));
  }

  public class Checker {
    private final String platformVersion = platformSettingsService.getPlatformVersion();
    private final Set<String> injectors =
        injectorService.findAll().stream().map(Injector::getType).collect(Collectors.toSet());
    // An injector is enabled from the catalogue integration of the same slug, when there is one.
    private final Map<String, String> integrationsBySlug =
        catalogConnectorService.findAll().stream()
            .filter(connector -> connector.getSlug() != null)
            .collect(
                Collectors.toMap(
                    CatalogConnector::getSlug, CatalogConnector::getId, (first, other) -> first));
    private final Function<String, Status> integrationStatus;

    private Checker(Function<String, Status> integrationStatus) {
      this.integrationStatus = integrationStatus;
    }

    public List<MissingRequirement> missing(CatalogScenario item) {
      Requires requires = read(item);
      List<MissingRequirement> missing = new ArrayList<>();
      if (requires == null) {
        return missing;
      }
      if (requires.openaev() != null
          && !VersionConstraint.allows(requires.openaev(), platformVersion)) {
        missing.add(new MissingRequirement("OpenAEV " + requires.openaev(), null, null));
      }
      Optional.ofNullable(requires.injectors()).orElse(List.of()).stream()
          .filter(type -> !injectors.contains(type))
          .forEach(type -> missing.add(injector(type)));
      return missing;
    }

    private MissingRequirement injector(String type) {
      String integrationId = integrationsBySlug.get(type);
      return new MissingRequirement(
          "Injector " + type, integrationId, integrationStatus.apply(integrationId));
    }
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
