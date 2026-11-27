package io.openaev.rest.atomic_testing;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.TENANT_ATOMIC_TESTING_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.atomic_testing.form.AtomicTestingInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.CredentialSecretReferenceFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * An atomic testing update links the teams, tags, assets, asset groups and secret references of the
 * atomic testing's own tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The ids of the body
 * resolve in the tenant of the atomic testing alone. The links are read from the inject link tables
 * through JDBC on the test connection, which the statement inspector does not rewrite.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Atomic testing update links only the rows of the atomic testing tenant")
class AtomicTestingLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  /** The link tables of an inject, each read by inject id, with the kind of row they hold. */
  private static final Map<String, String> LINKS =
      Map.of(
          "team", "SELECT team_id FROM injects_teams WHERE inject_id = ?",
          "tag", "SELECT tag_id FROM injects_tags WHERE inject_id = ?",
          "asset", "SELECT asset_id FROM injects_assets WHERE inject_id = ?",
          "asset group", "SELECT asset_group_id FROM injects_asset_groups WHERE inject_id = ?",
          "secret reference",
              "SELECT secret_reference_id FROM injects_secret_references WHERE inject_id = ?");

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private TeamRepository teamRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private SecretReferenceRepository secretReferenceRepository;

  private String tenantB;
  private Rows inDefault;
  private Rows inB;

  /** One row of each linked kind, all in one tenant. */
  private record Rows(
      String team, String tag, String asset, String assetGroup, String secretReference) {

    String of(String kind) {
      return switch (kind) {
        case "team" -> team;
        case "tag" -> tag;
        case "asset" -> asset;
        case "asset group" -> assetGroup;
        default -> secretReference;
      };
    }
  }

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("atomic-testing-link-b").getId();
    injectorContractFixture.getWellKnownSingleEmailContract();
    inDefault = saveRows(Tenant.DEFAULT_TENANT_UUID);
    inB = saveRows(tenantB);
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
        "given an atomic testing of the default tenant when the update body names rows of each"
            + " tenant then only the rows of the default tenant are linked")
    void given_atomicTestingOfDefaultTenant_should_linkOnlyItsOwnRowsOnUpdate() throws Exception {
      // -- Arrange --
      String atomicTesting = createInDefaultTenant();
      armRequest();
      AtomicTestingInput input = InjectFixture.createAtomicTesting("atomic-link-update", null);
      input.setTeams(List.of(inDefault.team(), inB.team()));
      input.setTagIds(List.of(inDefault.tag(), inB.tag()));
      input.setAssets(List.of(inDefault.asset(), inB.asset()));
      input.setAssetGroups(List.of(inDefault.assetGroup(), inB.assetGroup()));
      input.setSecretReferences(List.of(inDefault.secretReference(), inB.secretReference()));

      // -- Act --
      mvc.perform(
              put(ATOMIC_TESTING_URI + "/" + atomicTesting)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertSoftly(
          softly ->
              LINKS.forEach(
                  (kind, sql) ->
                      softly
                          .assertThat(column(entityManager, sql, atomicTesting))
                          .as(
                              "%s linked: kept %s, dropped %s",
                              kind, inDefault.of(kind), inB.of(kind))
                          .containsExactly(inDefault.of(kind))));
    }

    @Test
    @DisplayName(
        "given an atomic testing of the default tenant when the tags body names a tag of each"
            + " tenant then only the tag of the default tenant is linked")
    void given_atomicTestingOfDefaultTenant_should_linkOnlyItsOwnTag() throws Exception {
      // -- Arrange --
      String atomicTesting = createInDefaultTenant();
      armRequest();

      // -- Act --
      mvc.perform(
              put(ATOMIC_TESTING_URI + "/" + atomicTesting + "/tags")
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(Map.of("atomic_tags", List.of(inDefault.tag(), inB.tag()))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, LINKS.get("tag"), atomicTesting))
          .as("tags linked: kept %s, dropped %s", inDefault.tag(), inB.tag())
          .containsExactly(inDefault.tag());
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given an atomic testing of the default tenant when the update body under its path names"
            + " its rows then they are linked")
    void given_atomicTestingOfDefaultTenant_should_linkItsRowsOnThePrefixedRoute()
        throws Exception {
      // -- Arrange --
      String atomicTesting = createInDefaultTenant();
      armRequest();
      AtomicTestingInput input = InjectFixture.createAtomicTesting("atomic-link-prefixed", null);
      input.setTeams(List.of(inDefault.team()));
      input.setTagIds(List.of(inDefault.tag()));
      input.setAssets(List.of(inDefault.asset()));
      input.setAssetGroups(List.of(inDefault.assetGroup()));
      input.setSecretReferences(List.of(inDefault.secretReference()));

      // -- Act --
      mvc.perform(
              put(
                      TENANT_ATOMIC_TESTING_URI + "/{injectId}",
                      Tenant.DEFAULT_TENANT_UUID,
                      atomicTesting)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertSoftly(
          softly ->
              LINKS.forEach(
                  (kind, sql) ->
                      softly
                          .assertThat(column(entityManager, sql, atomicTesting))
                          .as("%s linked", kind)
                          .containsExactly(inDefault.of(kind))));
    }
  }

  // -- Helpers --

  /** Creates an atomic testing in the default tenant through its own prefixed route. */
  private String createInDefaultTenant() throws Exception {
    String response =
        mvc.perform(
                post(TENANT_ATOMIC_TESTING_URI, Tenant.DEFAULT_TENANT_UUID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(InjectFixture.createAtomicTesting("atomic-link", null)))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(response, "$.inject_id");
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private Rows saveRows(String tenantId) {
    Team team = TeamFixture.createTeamWithName("atomic-link-" + UUID.randomUUID());
    team.setTenant(new Tenant(tenantId));
    Endpoint endpoint = EndpointFixture.createEndpoint("atomic-link-" + UUID.randomUUID());
    endpoint.setTenant(new Tenant(tenantId));
    AssetGroup assetGroup =
        AssetGroupFixture.createDefaultAssetGroup("atomic-link-" + UUID.randomUUID());
    assetGroup.setTenant(new Tenant(tenantId));
    SecretReference secretReference =
        CredentialSecretReferenceFixture.getUsernamePasswordReference();
    secretReference.setTenant(new Tenant(tenantId));
    return new Rows(
        teamRepository.save(team).getId(),
        saveTag(tagRepository, tenantId),
        endpointRepository.save(endpoint).getId(),
        assetGroupRepository.save(assetGroup).getId(),
        secretReferenceRepository.save(secretReference).getId());
  }

  /**
   * Each row of the other tenant carries that tenant, then the ambient tenant is reset to the
   * default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(column(entityManager, "SELECT tenant_id FROM teams WHERE team_id = ?", inB.team()))
        .containsExactly(tenantB);
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", inB.tag()))
        .containsExactly(tenantB);
    assertThat(
            column(entityManager, "SELECT tenant_id FROM assets WHERE asset_id = ?", inB.asset()))
        .containsExactly(tenantB);
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM asset_groups WHERE asset_group_id = ?",
                inB.assetGroup()))
        .containsExactly(tenantB);
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM secret_references WHERE secret_reference_id = ?",
                inB.secretReference()))
        .containsExactly(tenantB);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
