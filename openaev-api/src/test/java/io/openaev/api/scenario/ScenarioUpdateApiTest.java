package io.openaev.api.scenario;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.service.catalog.scenario.update.CatalogScenarioUpdateService.CONFLICT_LEFT;
import static io.openaev.service.catalog.scenario.update.CatalogScenarioUpdateService.NOT_INSTALLED;
import static io.openaev.service.catalog.scenario.update.CatalogScenarioUpdater.CHAINED_REFUSED;
import static io.openaev.service.catalog.scenario.update.CatalogScenarioUpdater.IMPORTED_LINKS_REFUSED;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.SCENARIO_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.chainedScenarioPackage;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.releasedPayload;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioItem;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioPackage;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.syncRequest;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.api.catalog.dto.scenario.TimeBasedScenarioInstallInput;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectDependencyConditions.DependencyMode;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Variable;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.repository.CatalogHistoryRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.service.VariableService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogReleaseFixture.ReleasedPayload;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,CATALOG")
@DisplayName("Catalogue scenario update and rollback")
class ScenarioUpdateApiTest extends IntegrationTest {

  private static final ReleasedPayload RELEASED = releasedPayload();
  private static final String INSTALLED = "1.0.0";
  private static final String NEWER = "1.1.0";
  private static final String SAME_CONTENT = "1.0.1";
  private static final String RESHAPED = "1.2.0";
  private static final String LINKING_CHALLENGES = "1.3.0";
  private static final String CHAINED = "2.0.0";
  private static final String NEW_TITLE = "whoami, verbose";
  private static final String MY_TITLE = "My whoami";
  private static final String TARGET = "10.0.0.1";
  private static final String MOVED_TARGET = "10.0.0.2";
  private static final String WARNING_ID = "4d3c2b1a-0000-4000-8000-000000000002";
  private static final String WARNING_TITLE = "Warn the SOC";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private VariableService variableService;
  @Autowired private CatalogHistoryRepository catalogHistoryRepository;

  @BeforeEach
  void publishAndInstall() throws Exception {
    markingDefinitionComposer.reset();
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    injectorFixture.getWellKnownOaevImplantInjector();
    Map<String, byte[]> archives = new LinkedHashMap<>();
    archives.put(INSTALLED, scenarioPackage(RELEASED.file()));
    archives.put(SAME_CONTENT, scenarioPackage(RELEASED.file()));
    archives.put(NEWER, scenarioPackage(RELEASED.file(), newer()));
    archives.put(RESHAPED, scenarioPackage(RELEASED.file(), newer().andThen(reshaped())));
    archives.put(LINKING_CHALLENGES, scenarioPackage(RELEASED.file(), linkingChallenges()));
    archives.put(CHAINED, chainedScenarioPackage(RELEASED.file()));
    publish(archives);
  }

  // -- HELPERS --

  // The next version renames the inject and adds a variable.
  private static Consumer<ObjectNode> newer() {
    return export -> {
      ((ObjectNode) export.get("scenario_injects").get(0)).put("inject_title", NEW_TITLE);
      ObjectNode variable = export.putArray("scenario_variables").addObject();
      variable.put("variable_id", "4d3c2b1a-0000-4000-8000-000000000001");
      variable.put("variable_key", "target");
      variable.put("variable_value", TARGET);
      variable.put("variable_type", "String");
    };
  }

  // The version after makes the scan wait for a warning email and moves the target.
  private static Consumer<ObjectNode> reshaped() {
    return export -> {
      ((ObjectNode) export.get("scenario_variables").get(0)).put("variable_value", MOVED_TARGET);
      ArrayNode injects = (ArrayNode) export.get("scenario_injects");
      ObjectNode warning = injects.addObject();
      warning.put("inject_id", WARNING_ID);
      warning.put("inject_title", WARNING_TITLE);
      warning.putObject("inject_content").put("subject", "A scan runs tonight");
      warning.putObject("inject_injector_contract").put("injector_contract_id", EMAIL_DEFAULT);
      ObjectNode scan = (ObjectNode) injects.get(0);
      ObjectNode dependency = scan.putArray("inject_depends_on").addObject();
      ObjectNode relationship = dependency.putObject("dependency_relationship");
      relationship.put("inject_parent_id", WARNING_ID);
      relationship.put("inject_children_id", scan.path("inject_id").asText());
      ObjectNode condition = dependency.putObject("dependency_condition");
      condition.put("mode", DependencyMode.and.name());
      ObjectNode executed = condition.putArray("conditions").addObject();
      executed.put("key", "Execution");
      executed.put("value", true);
      executed.put("operator", "eq");
    };
  }

  private static Consumer<ObjectNode> linkingChallenges() {
    return export ->
        export.putArray("scenario_challenges").addObject().put("challenge_name", "Find the flag");
  }

  private void publish(Map<String, byte[]> archives) throws Exception {
    List<Map<String, Object>> items = new ArrayList<>();
    Map<String, byte[]> files = new LinkedHashMap<>();
    archives.forEach(
        (version, archive) -> {
          Map<String, Object> item = scenarioItem(version, "VERIFIED", archive);
          item.put("payloads", List.of(Map.of("external_id", PAYLOAD_ID, "version", INSTALLED)));
          if (CHAINED.equals(version)) {
            item.put("type", "chained");
          }
          items.add(item);
          files.put(scenarioFileName(version), archive);
        });
    files.put(payloadFileName(INSTALLED), RELEASED.file());
    mvc.perform(
            syncRequest(
                    mapper.writeValueAsBytes(
                        manifest(
                            "2026.11.20",
                            items,
                            List.of(payloadVersion(INSTALLED, "VERIFIED", RELEASED)))),
                    files,
                    TENANT_CATALOG_URI + "/sync",
                    DEFAULT_TENANT_UUID)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  // The test transaction never commits: flush what a request changed, as its commit would.
  private ResultActions committed(MockHttpServletRequestBuilder request) throws Exception {
    ResultActions result = mvc.perform(request.with(csrf()));
    entityManager.flush();
    entityManager.clear();
    return result;
  }

  private Scenario install() throws Exception {
    String body =
        committed(
                post(
                        TENANT_CATALOG_URI + "/scenarios/{externalId}/versions/{version}/install",
                        DEFAULT_TENANT_UUID,
                        SCENARIO_ID,
                        INSTALLED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            new TimeBasedScenarioInstallInput(null, null, null, null))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return scenarioRepository
        .findById(mapper.readTree(body).path("installed_id").asText())
        .orElseThrow();
  }

  private ResultActions previewUpdate(Scenario scenario, String version) throws Exception {
    return committed(
        get(
            TENANT_SCENARIO_URI + "/{scenarioId}/versions/{version}/update-preview",
            DEFAULT_TENANT_UUID,
            scenario.getId(),
            version));
  }

  private ResultActions update(Scenario scenario, Map<String, String> choices) throws Exception {
    return update(scenario, NEWER, choices);
  }

  private ResultActions update(Scenario scenario, String version, Map<String, String> choices)
      throws Exception {
    return committed(
        post(
                TENANT_SCENARIO_URI + "/{scenarioId}/versions/{version}/update",
                DEFAULT_TENANT_UUID,
                scenario.getId(),
                version)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("scenario_update_choices", choices))));
  }

  private ResultActions rollback(Scenario scenario, String revisionId) throws Exception {
    return committed(
        post(
                TENANT_SCENARIO_URI + "/{scenarioId}/revisions/{revisionId}/rollback",
                DEFAULT_TENANT_UUID,
                scenario.getId(),
                revisionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"));
  }

  private ResultActions revisions(Scenario scenario) throws Exception {
    return committed(
            get(
                TENANT_SCENARIO_URI + "/{scenarioId}/revisions",
                DEFAULT_TENANT_UUID,
                scenario.getId()))
        .andExpect(status().isOk());
  }

  // The revision an update kept of the content the given version had.
  private String revisionId(Scenario scenario, String catalogVersion) throws Exception {
    String body = revisions(scenario).andReturn().getResponse().getContentAsString();
    return StreamSupport.stream(mapper.readTree(body).spliterator(), false)
        .filter(
            revision ->
                catalogVersion.equals(revision.path("scenario_revision_catalog_version").asText()))
        .map(revision -> revision.path("scenario_revision_id").asText())
        .findFirst()
        .orElseThrow();
  }

  private String onlyRevisionId(Scenario scenario) throws Exception {
    String body =
        revisions(scenario)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].scenario_revision_reason").value("UPDATE"))
            .andExpect(jsonPath("$[0].scenario_revision_catalog_version").value(INSTALLED))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).get(0).path("scenario_revision_id").asText();
  }

  private Inject onlyInject(Scenario scenario) {
    return injectRepository.findByScenarioId(scenario.getId()).iterator().next();
  }

  private Inject injectTitled(Scenario scenario, String title) {
    return injectRepository.findByScenarioId(scenario.getId()).stream()
        .filter(inject -> title.equals(inject.getTitle()))
        .findFirst()
        .orElseThrow();
  }

  private List<Variable> variables(Scenario scenario) {
    return variableService.variablesFromScenario(scenario.getId());
  }

  private void renameInjectLocally(Scenario scenario) {
    Inject inject = onlyInject(scenario);
    inject.setTitle(MY_TITLE);
    injectRepository.save(inject);
    entityManager.flush();
    entityManager.clear();
  }

  private String installedVersion(Scenario scenario) {
    return scenarioRepository.findById(scenario.getId()).orElseThrow().getCatalogVersion();
  }

  // -- UPDATE --

  @Nested
  @DisplayName("Updating")
  class Updating {

    @Test
    @DisplayName("lists the versions offered after the installed one, newest first")
    void given_installedScenario_should_listNewerVersions() throws Exception {
      // Arrange
      Scenario scenario = install();

      // Act & Assert
      committed(get(TENANT_SCENARIO_URI + "/{scenarioId}", DEFAULT_TENANT_UUID, scenario.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scenario_version").value(INSTALLED));
      committed(
              get(
                  TENANT_SCENARIO_URI + "/{scenarioId}/newer-versions",
                  DEFAULT_TENANT_UUID,
                  scenario.getId()))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$[*].catalog_scenario_version")
                  .value(contains(CHAINED, LINKING_CHALLENGES, RESHAPED, NEWER, SAME_CONTENT)));
    }

    @Test
    @DisplayName("shows nothing to change for a version with the same content")
    void given_sameContent_should_showNoChange() throws Exception {
      // Arrange
      Scenario scenario = install();

      // Act
      String body =
          previewUpdate(scenario, SAME_CONTENT)
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(body).isArray().isEmpty();
    }

    @Test
    @DisplayName("takes what the version changes on a scenario the client left as it was")
    void given_untouchedScenario_should_takeTheVersion() throws Exception {
      // Arrange
      Scenario scenario = install();

      // Act
      String preview =
          previewUpdate(scenario, NEWER)
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      update(scenario, Map.of()).andExpect(status().isNoContent());

      // Assert
      assertThatJson(preview)
          .inPath("$[*].scenario_change_status")
          .isArray()
          .containsOnly("TAKE_APPLIED");
      assertThat(onlyInject(scenario).getTitle()).isEqualTo(NEW_TITLE);
      assertThat(variables(scenario))
          .extracting(Variable::getKey, Variable::getValue)
          .containsExactly(tuple("target", TARGET));
      assertThat(installedVersion(scenario)).isEqualTo(NEWER);
      assertThat(onlyRevisionId(scenario)).isNotBlank();
      assertThat(catalogHistoryRepository.findAll())
          .extracting(CatalogHistory::getAction)
          .contains(CatalogHistory.Action.UPDATE);
    }

    @Test
    @DisplayName("refuses a field both changed until the client chooses a side")
    void given_conflict_should_waitForTheClientChoice() throws Exception {
      // Arrange
      Scenario scenario = install();
      renameInjectLocally(scenario);
      String conflictId = "inject:" + onlyInject(scenario).getCatalogId() + "/title";

      // Act & Assert
      previewUpdate(scenario, NEWER)
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$[?(@.scenario_change_id == '" + conflictId + "')].scenario_change_status")
                  .value("CONFLICT"));
      update(scenario, Map.of())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(CONFLICT_LEFT));
      update(scenario, Map.of(conflictId, "KEEP_MINE")).andExpect(status().isNoContent());
      assertThat(onlyInject(scenario).getTitle()).isEqualTo(MY_TITLE);
      assertThat(installedVersion(scenario)).isEqualTo(NEWER);
    }

    @Test
    @DisplayName("refuses a scenario not installed from the catalogue")
    void given_localScenario_should_refuse() throws Exception {
      // Arrange
      Scenario local = ScenarioFixture.getScenario();
      local.setTenant(new Tenant(DEFAULT_TENANT_UUID));
      Scenario scenario = scenarioRepository.save(local);

      // Act & Assert
      previewUpdate(scenario, NEWER)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(NOT_INSTALLED));
    }
  }

  // -- ROLLBACK --

  @Nested
  @DisplayName("Rolling back")
  class RollingBack {

    @Test
    @DisplayName("brings back the content and the version the scenario had before an update")
    void given_updatedScenario_should_rollBackToItsRevision() throws Exception {
      // Arrange
      Scenario scenario = install();
      update(scenario, Map.of()).andExpect(status().isNoContent());
      String revisionId = onlyRevisionId(scenario);

      // Act
      committed(
              get(
                  TENANT_SCENARIO_URI + "/{scenarioId}/revisions/{revisionId}/rollback-preview",
                  DEFAULT_TENANT_UUID,
                  scenario.getId(),
                  revisionId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isNotEmpty());
      rollback(scenario, revisionId).andExpect(status().isNoContent());

      // Assert
      assertThat(onlyInject(scenario).getTitle()).isEqualTo("whoami");
      assertThat(installedVersion(scenario)).isEqualTo(INSTALLED);
    }

    @Test
    @DisplayName("removes the inject, the link and the value a later version brought")
    void given_versionAddingALinkedInject_should_writeItThenRollItBack() throws Exception {
      // Arrange
      injectorContractFixture.getWellKnownSingleEmailContract();
      Scenario scenario = install();
      update(scenario, Map.of()).andExpect(status().isNoContent());

      // Act
      update(scenario, RESHAPED, Map.of()).andExpect(status().isNoContent());

      // Assert
      assertThat(injectTitled(scenario, WARNING_TITLE).getInjectorContract())
          .map(InjectorContract::getId)
          .hasValue(EMAIL_DEFAULT);
      assertThat(injectTitled(scenario, NEW_TITLE).getDependsOn())
          .extracting(
              dependency -> dependency.getCompositeId().getInjectParent().getCatalogId(),
              dependency -> dependency.getInjectDependencyCondition().getMode())
          .containsExactly(tuple(WARNING_ID, DependencyMode.and));
      assertThat(variables(scenario))
          .extracting(Variable::getKey, Variable::getValue)
          .containsExactly(tuple("target", MOVED_TARGET));

      // Act
      rollback(scenario, revisionId(scenario, NEWER)).andExpect(status().isNoContent());

      // Assert
      assertThat(injectRepository.findByScenarioId(scenario.getId()))
          .singleElement()
          .satisfies(
              scan -> {
                assertThat(scan.getTitle()).isEqualTo(NEW_TITLE);
                assertThat(scan.getDependsOn()).isEmpty();
              });
      assertThat(variables(scenario))
          .extracting(Variable::getKey, Variable::getValue)
          .containsExactly(tuple("target", TARGET));
      assertThat(installedVersion(scenario)).isEqualTo(NEWER);
    }
  }

  // -- REFUSALS --

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Refusing")
  class Refusing {

    static Stream<Arguments> unsupportedVersions() {
      return Stream.of(
          Arguments.of(LINKING_CHALLENGES, IMPORTED_LINKS_REFUSED),
          Arguments.of(CHAINED, CHAINED_REFUSED));
    }

    @ParameterizedTest
    @MethodSource("unsupportedVersions")
    @DisplayName("refuses a version an update cannot write yet")
    void given_unsupportedVersion_should_refuseTheUpdate(String version, String message)
        throws Exception {
      // Arrange
      Scenario scenario = install();

      // Act & Assert
      previewUpdate(scenario, version)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(message));
    }
  }
}
