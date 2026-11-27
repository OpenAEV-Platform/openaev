package io.openaev.service.catalog.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.service.catalog.scenario.MissingRequirement.Status;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Catalogue scenario requirements")
class CatalogScenarioRequirementsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PLATFORM_VERSION = "2.0.0";
  private static final String INJECTOR = "openaev_missing";

  private static CatalogScenario requiring(Map<String, Object> requires) throws Exception {
    CatalogScenario item = new CatalogScenario();
    item.setRequires(MAPPER.writeValueAsString(requires));
    return item;
  }

  private static CatalogConnector integration(String id, String title) {
    CatalogConnector integration = new CatalogConnector();
    integration.setId(id);
    integration.setTitle(title);
    integration.setSlug(INJECTOR);
    return integration;
  }

  @Nested
  @DisplayName("Checking a scenario")
  class Checking {

    @Test
    @DisplayName("names the platform version a scenario needs when this one is older")
    void given_olderPlatform_should_nameTheVersionNeeded() throws Exception {
      // Arrange
      CatalogScenarioRequirements.Checker checker =
          new CatalogScenarioRequirements.Checker(MAPPER, PLATFORM_VERSION, Set.of(), Map.of());

      // Act
      List<MissingRequirement> missing = checker.missing(requiring(Map.of("openaev", ">=3.0")));

      // Assert
      assertThat(missing)
          .containsExactly(new MissingRequirement("OpenAEV >=3.0", "OpenAEV >=3.0", null, null));
    }

    @Test
    @DisplayName("names a missing injector by its label when its integration has no title")
    void given_integrationWithoutTitle_should_nameTheInjector() throws Exception {
      // Arrange
      CatalogScenarioRequirements.Checker checker =
          new CatalogScenarioRequirements.Checker(
              MAPPER, PLATFORM_VERSION, Set.of(), Map.of(INJECTOR, integration("c1", " ")));

      // Act
      List<MissingRequirement> missing =
          checker.missing(requiring(Map.of("injectors", List.of(INJECTOR))));

      // Assert
      assertThat(missing)
          .containsExactly(
              new MissingRequirement("Injector " + INJECTOR, "Injector " + INJECTOR, "c1", null));
    }

    @Test
    @DisplayName("tells a missing injector whose integration is deployed is not running")
    void given_deployedIntegration_should_sayItIsNotRunning() throws Exception {
      // Arrange
      CatalogScenarioRequirements.Checker checker =
          new CatalogScenarioRequirements.StatusChecker(
              MAPPER,
              PLATFORM_VERSION,
              Set.of(),
              Map.of(INJECTOR, integration("c1", "Missing injector")),
              Set.of("c1"));

      // Act
      List<MissingRequirement> missing =
          checker.missing(requiring(Map.of("injectors", List.of(INJECTOR))));

      // Assert
      assertThat(missing)
          .containsExactly(
              new MissingRequirement(
                  "Injector " + INJECTOR, "Missing injector", "c1", Status.NOT_RUNNING));
    }
  }
}
