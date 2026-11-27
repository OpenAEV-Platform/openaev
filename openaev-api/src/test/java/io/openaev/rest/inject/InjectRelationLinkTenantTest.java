package io.openaev.rest.inject;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
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
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.CredentialSecretReferenceFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RowTenantScope;
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
 * An inject links only the tags, assets, asset groups and secret references of its own tenant when
 * a create or an edit names rows of several tenants.
 *
 * <p>The simulation and the inject live in the default tenant, the one the ambient thread-local
 * falls back to. On the non-prefixed route {@code X-Tenant-Ids} can name several tenants, and these
 * edits carry no tenant selector, so the request scope holds all of them. The ids of the body
 * resolve in the tenant of the inject alone. The links are read through {@code injects_tags},
 * {@code injects_assets}, {@code injects_asset_groups} and {@code injects_secret_references} on the
 * test connection, which the statement inspector does not rewrite. The class arms the production
 * list.
 *
 * <p>The create endpoint does not apply the asset groups of the body unless the injector contract's
 * content declares an asset groups field, so the create test covers tags, assets and secret
 * references; the asset groups site is covered on the update endpoint, which applies it
 * unconditionally.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName(
    "An inject links only the tags, assets, asset groups and secret references of its own tenant")
class InjectRelationLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String INJECT_TAGS = "SELECT tag_id FROM injects_tags WHERE inject_id = ?";
  private static final String INJECT_ASSETS =
      "SELECT asset_id FROM injects_assets WHERE inject_id = ?";
  private static final String INJECT_ASSET_GROUPS =
      "SELECT asset_group_id FROM injects_asset_groups WHERE inject_id = ?";
  private static final String INJECT_SECRET_REFERENCES =
      "SELECT secret_reference_id FROM injects_secret_references WHERE inject_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseService exerciseService;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private TagRepository tagRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private SecretReferenceRepository secretReferenceRepository;
  @Autowired private RowTenantScope rowTenantScope;

  private String otherTenant;
  private String exercise;
  private String injectorContract;
  private String tagInOther;
  private String tagInDefault;
  private String assetInOther;
  private String assetInDefault;
  private String groupInOther;
  private String groupInDefault;
  private String secretReferenceInOther;
  private String secretReferenceInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("inject-relation-link-o").getId();

    Exercise createdExercise = new Exercise();
    createdExercise.setName("inject-relation-link-exercise");
    createdExercise.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    exercise = exerciseService.createExercise(createdExercise).getId();

    // The other tenant's rows are written in that tenant's scope, as a request of that tenant
    // would write them; the default tenant's rows are written in the test transaction's scope.
    // The flush runs the inserts here: left pending, they would be flushed by the next service
    // call, under the default-tenant scope its own transaction sets.
    rowTenantScope.write(
        otherTenant,
        () -> {
          tagInOther = saveTag(tagRepository, otherTenant);
          assetInOther = saveAsset(otherTenant);
          groupInOther = saveGroup(otherTenant);
          secretReferenceInOther = saveSecretReference(otherTenant);
          entityManager.flush();
        });
    tagInDefault = saveTag(tagRepository, Tenant.DEFAULT_TENANT_UUID);
    assetInDefault = saveAsset(Tenant.DEFAULT_TENANT_UUID);
    groupInDefault = saveGroup(Tenant.DEFAULT_TENANT_UUID);
    secretReferenceInDefault = saveSecretReference(Tenant.DEFAULT_TENANT_UUID);

    TenantContext.clearCurrentTenant();
    injectorContract = injectorContractFixture.getWellKnownSingleEmailContract().getId();
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
        "given a simulation of the default tenant when an inject is created with tags, assets"
            + " and secret references of each tenant then only the row of the default tenant is"
            + " linked for each")
    void given_bodyNamingBothTenants_should_linkOnlyTheDefaultTenantRow_onCreateInject()
        throws Exception {
      // -- Arrange --
      armRequest();
      InjectInput input = baseInput();
      input.setTagIds(List.of(tagInOther, tagInDefault));
      input.setAssets(List.of(assetInOther, assetInDefault));
      input.setSecretReferences(List.of(secretReferenceInOther, secretReferenceInDefault));

      // -- Act --
      String response =
          mvc.perform(
                  post(EXERCISE_URI + "/{exerciseId}/injects", exercise)
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String injectId = JsonPath.read(response, "$.inject_id");
      assertSoftly(
          softly -> {
            softly
                .assertThat(column(entityManager, INJECT_TAGS, injectId))
                .as("tags linked: kept %s, dropped %s", tagInDefault, tagInOther)
                .containsExactly(tagInDefault);
            softly
                .assertThat(column(entityManager, INJECT_ASSETS, injectId))
                .as("assets linked: kept %s, dropped %s", assetInDefault, assetInOther)
                .containsExactly(assetInDefault);
            softly
                .assertThat(column(entityManager, INJECT_SECRET_REFERENCES, injectId))
                .as(
                    "secret references linked: kept %s, dropped %s",
                    secretReferenceInDefault, secretReferenceInOther)
                .containsExactly(secretReferenceInDefault);
          });
    }

    @Test
    @DisplayName(
        "given a simulation inject of the default tenant when it is edited with tags, assets,"
            + " asset groups and secret references of each tenant then only the row of the"
            + " default tenant is linked for each")
    void given_bodyNamingBothTenants_should_linkOnlyTheDefaultTenantRow_onUpdateInject()
        throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createInject();
      InjectInput input = baseInput();
      input.setTagIds(List.of(tagInOther, tagInDefault));
      input.setAssets(List.of(assetInOther, assetInDefault));
      input.setAssetGroups(List.of(groupInOther, groupInDefault));
      input.setSecretReferences(List.of(secretReferenceInOther, secretReferenceInDefault));

      // -- Act --
      mvc.perform(
              put(INJECT_URI + "/{exerciseId}/{injectId}", exercise, injectId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertSoftly(
          softly -> {
            softly
                .assertThat(column(entityManager, INJECT_TAGS, injectId))
                .as("tags linked: kept %s, dropped %s", tagInDefault, tagInOther)
                .containsExactly(tagInDefault);
            softly
                .assertThat(column(entityManager, INJECT_ASSETS, injectId))
                .as("assets linked: kept %s, dropped %s", assetInDefault, assetInOther)
                .containsExactly(assetInDefault);
            softly
                .assertThat(column(entityManager, INJECT_ASSET_GROUPS, injectId))
                .as("asset groups linked: kept %s, dropped %s", groupInDefault, groupInOther)
                .containsExactly(groupInDefault);
            softly
                .assertThat(column(entityManager, INJECT_SECRET_REFERENCES, injectId))
                .as(
                    "secret references linked: kept %s, dropped %s",
                    secretReferenceInDefault, secretReferenceInOther)
                .containsExactly(secretReferenceInDefault);
          });
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a simulation inject of the default tenant when the body under its path names the"
            + " tag, asset, asset group and secret reference of the other tenant then none of"
            + " them is linked")
    void given_bodyNamingTheOtherTenant_should_linkNothing() throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createInject();
      InjectInput input = baseInput();
      input.setTagIds(List.of(tagInOther));
      input.setAssets(List.of(assetInOther));
      input.setAssetGroups(List.of(groupInOther));
      input.setSecretReferences(List.of(secretReferenceInOther));

      // -- Act --
      mvc.perform(
              put(
                      TENANT_INJECT_URI + "/{exerciseId}/{injectId}",
                      Tenant.DEFAULT_TENANT_UUID,
                      exercise,
                      injectId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, INJECT_TAGS, injectId)).isEmpty();
      assertThat(column(entityManager, INJECT_ASSETS, injectId)).isEmpty();
      assertThat(column(entityManager, INJECT_ASSET_GROUPS, injectId)).isEmpty();
      assertThat(column(entityManager, INJECT_SECRET_REFERENCES, injectId)).isEmpty();
    }

    @Test
    @DisplayName(
        "given a simulation inject of the default tenant when the body under its path names its"
            + " own tag, asset, asset group and secret reference then each is linked")
    void given_bodyNamingItsOwnRows_should_linkThem() throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createInject();
      InjectInput input = baseInput();
      input.setTagIds(List.of(tagInDefault));
      input.setAssets(List.of(assetInDefault));
      input.setAssetGroups(List.of(groupInDefault));
      input.setSecretReferences(List.of(secretReferenceInDefault));

      // -- Act --
      mvc.perform(
              put(
                      TENANT_INJECT_URI + "/{exerciseId}/{injectId}",
                      Tenant.DEFAULT_TENANT_UUID,
                      exercise,
                      injectId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, INJECT_TAGS, injectId)).containsExactly(tagInDefault);
      assertThat(column(entityManager, INJECT_ASSETS, injectId)).containsExactly(assetInDefault);
      assertThat(column(entityManager, INJECT_ASSET_GROUPS, injectId))
          .containsExactly(groupInDefault);
      assertThat(column(entityManager, INJECT_SECRET_REFERENCES, injectId))
          .containsExactly(secretReferenceInDefault);
    }
  }

  // -- Helpers --

  private InjectInput baseInput() {
    InjectInput input = new InjectInput();
    input.setTitle("inject-relation-link");
    input.setInjectorContract(injectorContract);
    input.setDependsDuration(0L);
    return input;
  }

  private String createInject() throws Exception {
    String response =
        mvc.perform(
                post(EXERCISE_URI + "/{exerciseId}/injects", exercise)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(baseInput()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.inject_id");
  }

  private String saveAsset(String tenantId) {
    io.openaev.database.model.Asset asset =
        AssetFixture.createDefaultAsset("inject-relation-asset-" + tenantId);
    asset.setTenant(new Tenant(tenantId));
    return assetRepository.save(asset).getId();
  }

  private String saveGroup(String tenantId) {
    AssetGroup group =
        AssetGroupFixture.createDefaultAssetGroup("inject-relation-group-" + tenantId);
    return assetGroupService.createAssetGroup(group, tenantId).getId();
  }

  private String saveSecretReference(String tenantId) {
    CredentialSecretReference reference =
        CredentialSecretReferenceFixture.getUsernamePasswordReference();
    reference.setName("inject-relation-secret-" + tenantId);
    reference.setTenant(new Tenant(tenantId));
    return secretReferenceRepository.save((SecretReference) reference).getId();
  }

  /**
   * Each seeded row carries the tenant the simulation relies on, then the ambient tenant is reset
   * to the default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInOther))
        .containsExactly(otherTenant);
    assertThat(
            column(
                entityManager, "SELECT tenant_id FROM exercises WHERE exercise_id = ?", exercise))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
