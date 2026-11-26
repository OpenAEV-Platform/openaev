package io.openaev.rest.inject;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.inject.form.InjectAssistantInput;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inject assistant targets the asset groups and the endpoints of the scenario's own tenant.
 *
 * <p>The scenario lives in the default tenant, the one the ambient thread-local falls back to. On
 * the non-prefixed route {@code X-Tenant-Ids} can name several tenants, and the assistant carries
 * no tenant selector, so the request scope holds all of them. The asset group ids and the asset ids
 * of the body resolve in the tenant of the scenario alone. The generated injects hold their asset
 * groups in {@code injects_asset_groups} and their endpoints in {@code injects_assets}, read
 * through JDBC on the test connection, which the statement inspector does not rewrite. The class
 * arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Inject assistant targets only the asset groups and endpoints of the scenario tenant")
class InjectAssistantAssetGroupTenantTest extends ProductionTablesIntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String INJECT_ASSET_GROUPS =
      "SELECT asset_group_id FROM injects_asset_groups WHERE inject_id = ?";
  private static final String INJECT_ASSETS =
      "SELECT asset_id FROM injects_assets WHERE inject_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioService scenarioService;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private EndpointService endpointService;
  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainComposer domainComposer;

  private String otherTenant;
  private String scenario;
  private String attackPattern;
  private String groupInOther;
  private String groupInDefault;
  private String endpointInOther;
  private String endpointInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("assistant-group-o").getId();
    builtinConnectorRegistration.register(
        manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);

    AttackPattern pattern =
        attackPatternRepository.save(
            AttackPatternFixture.createDefaultAttackPattern(Tenant.DEFAULT_TENANT_UUID));
    attackPattern = pattern.getId();
    // The contract the assistant picks for every platform and architecture of the attack pattern.
    Endpoint.PLATFORM_TYPE[] allPlatforms = Endpoint.PLATFORM_TYPE.values();
    Injector injector = injectorFixture.getWellKnownOaevImplantInjector();
    InjectorContract contract =
        InjectorContractFixture.createInjectorContractWithPlatforms(allPlatforms);
    contract.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    injectorContractComposer
        .forInjectorContract(contract)
        .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()).persist())
        .withInjector(injector)
        .withAttackPattern(attackPatternComposer.forAttackPattern(pattern))
        .withPayload(
            payloadComposer.forPayload(
                PayloadFixture.createDefaultCommandWithPlatformsAndArchitecture(
                    allPlatforms, Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES)))
        .persist();

    Scenario created = new Scenario();
    created.setName("assistant-group-scenario");
    created.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    scenario = scenarioService.createScenario(created).getId();

    groupInOther = saveGroup(otherTenant);
    groupInDefault = saveGroup(Tenant.DEFAULT_TENANT_UUID);
    endpointInOther = saveEndpoint(otherTenant);
    endpointInDefault = saveEndpoint(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body names an asset group of each tenant"
            + " then only the group of the default tenant is linked to the generated inject")
    void given_bodyNamingBothTenants_should_linkOnlyTheGroupOfDefaultTenant() throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  post(SCENARIO_URI + "/" + scenario + "/injects/assistant")
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(body(List.of(groupInOther, groupInDefault))))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String inject = JsonPath.read(response, "$[0].inject_id");
      assertThat(column(entityManager, INJECT_ASSET_GROUPS, inject))
          .as(
              "asset groups linked to the inject: kept %s, dropped %s",
              groupInDefault, groupInOther)
          .containsExactly(groupInDefault);
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body names an endpoint of each tenant"
            + " then only the endpoint of the default tenant is linked to the generated inject")
    void given_bodyNamingEndpointsOfBothTenants_should_linkOnlyTheEndpointOfDefaultTenant()
        throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  post(SCENARIO_URI + "/" + scenario + "/injects/assistant")
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          asJsonString(
                              bodyWithEndpoints(List.of(endpointInOther, endpointInDefault))))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String inject = JsonPath.read(response, "$[0].inject_id");
      assertThat(column(entityManager, INJECT_ASSETS, inject))
          .as(
              "endpoints linked to the inject: kept %s, dropped %s",
              endpointInDefault, endpointInOther)
          .containsExactly(endpointInDefault);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body under its path names the group of the"
            + " other tenant then no group is linked to the generated inject")
    void given_bodyNamingTheOtherTenant_should_linkNothing() throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  post(
                          TENANT_SCENARIO_URI + "/{scenarioId}/injects/assistant",
                          Tenant.DEFAULT_TENANT_UUID,
                          scenario)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(body(List.of(groupInOther))))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String inject = JsonPath.read(response, "$[0].inject_id");
      assertThat(column(entityManager, INJECT_ASSET_GROUPS, inject)).isEmpty();
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body under its path names its own group"
            + " then the group is linked to the generated inject")
    void given_bodyNamingItsOwnGroup_should_linkIt() throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  post(
                          TENANT_SCENARIO_URI + "/{scenarioId}/injects/assistant",
                          Tenant.DEFAULT_TENANT_UUID,
                          scenario)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(body(List.of(groupInDefault))))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String inject = JsonPath.read(response, "$[0].inject_id");
      assertThat(column(entityManager, INJECT_ASSET_GROUPS, inject))
          .containsExactly(groupInDefault);
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body under its path names its own"
            + " endpoint then the endpoint is linked to the generated inject")
    void given_bodyNamingItsOwnEndpoint_should_linkIt() throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  post(
                          TENANT_SCENARIO_URI + "/{scenarioId}/injects/assistant",
                          Tenant.DEFAULT_TENANT_UUID,
                          scenario)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(bodyWithEndpoints(List.of(endpointInDefault))))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String inject = JsonPath.read(response, "$[0].inject_id");
      assertThat(column(entityManager, INJECT_ASSETS, inject)).containsExactly(endpointInDefault);
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body under its path names an endpoint of"
            + " the other tenant and an own group then the endpoint is not linked to the"
            + " generated inject")
    void given_bodyNamingTheEndpointOfTheOtherTenant_should_notLinkIt() throws Exception {
      // -- Arrange --
      armRequest();
      InjectAssistantInput input = body(List.of(groupInDefault));
      input.setAssetIds(List.of(endpointInOther));

      // -- Act --
      String response =
          mvc.perform(
                  post(
                          TENANT_SCENARIO_URI + "/{scenarioId}/injects/assistant",
                          Tenant.DEFAULT_TENANT_UUID,
                          scenario)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String inject = JsonPath.read(response, "$[0].inject_id");
      assertThat(column(entityManager, INJECT_ASSETS, inject)).isEmpty();
    }
  }

  // -- Helpers --

  private InjectAssistantInput bodyWithEndpoints(List<String> endpoints) {
    InjectAssistantInput input = body(List.of());
    input.setAssetIds(endpoints);
    return input;
  }

  private String saveEndpoint(String tenantId) {
    return endpointService
        .createEndpoint(EndpointFixture.createEndpoint("assistant-" + tenantId), tenantId)
        .getId();
  }

  private InjectAssistantInput body(List<String> assetGroups) {
    InjectAssistantInput input = new InjectAssistantInput();
    input.setAssetGroupIds(assetGroups);
    input.setAssetIds(List.of());
    input.setAttackPatternIds(List.of(attackPattern));
    input.setInjectByTTPNumber(1);
    return input;
  }

  private String saveGroup(String tenantId) {
    AssetGroup group = AssetGroupFixture.createDefaultAssetGroup("assistant-" + tenantId);
    return assetGroupService.createAssetGroup(group, tenantId).getId();
  }

  /**
   * Each group carries the tenant the scenario relies on, then the ambient tenant is reset to the
   * default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM asset_groups WHERE asset_group_id = ?",
                groupInOther))
        .containsExactly(otherTenant);
    assertThat(
            column(
                entityManager, "SELECT tenant_id FROM scenarios WHERE scenario_id = ?", scenario))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
