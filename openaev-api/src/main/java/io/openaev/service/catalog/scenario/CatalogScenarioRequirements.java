package io.openaev.service.catalog.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest.Requires;
import io.openaev.database.model.Injector;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.service.InjectorService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.catalog.VersionConstraint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  private final ObjectMapper mapper;

  public List<String> missing(CatalogScenario item) {
    Requires requires = read(item);
    List<String> missing = new ArrayList<>();
    if (requires == null) {
      return missing;
    }
    if (requires.openaev() != null
        && !VersionConstraint.allows(
            requires.openaev(), platformSettingsService.getPlatformVersion())) {
      missing.add("OpenAEV " + requires.openaev());
    }
    Set<String> injectors =
        injectorService.findAll().stream().map(Injector::getType).collect(Collectors.toSet());
    Optional.ofNullable(requires.injectors()).orElse(List.of()).stream()
        .filter(type -> !injectors.contains(type))
        .forEach(type -> missing.add("Injector " + type));
    return missing;
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
