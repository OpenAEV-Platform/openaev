package io.openaev.service.catalog.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest.Requires;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.Injector;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.service.InjectorService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.utils.catalog.VersionConstraint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

  public List<MissingRequirement> missing(CatalogScenario item) {
    return missing(item, injectorTypes());
  }

  /** What each scenario of a list lacks, reading the injectors once. */
  public Function<CatalogScenario, List<MissingRequirement>> missingForEach() {
    Set<String> injectors = injectorTypes();
    return item -> missing(item, injectors);
  }

  private List<MissingRequirement> missing(CatalogScenario item, Set<String> injectors) {
    Requires requires = read(item);
    List<MissingRequirement> missing = new ArrayList<>();
    if (requires == null) {
      return missing;
    }
    if (requires.openaev() != null
        && !VersionConstraint.allows(
            requires.openaev(), platformSettingsService.getPlatformVersion())) {
      missing.add(new MissingRequirement("OpenAEV " + requires.openaev(), null));
    }
    Optional.ofNullable(requires.injectors()).orElse(List.of()).stream()
        .filter(type -> !injectors.contains(type))
        .forEach(type -> missing.add(injector(type)));
    return missing;
  }

  private Set<String> injectorTypes() {
    return injectorService.findAll().stream().map(Injector::getType).collect(Collectors.toSet());
  }

  // An injector is enabled from the catalogue integration of the same slug, when there is one.
  private MissingRequirement injector(String type) {
    return new MissingRequirement(
        "Injector " + type,
        catalogConnectorService.findBySlug(type).map(CatalogConnector::getId).orElse(null));
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
