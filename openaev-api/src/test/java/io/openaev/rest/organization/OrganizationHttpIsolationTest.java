package io.openaev.rest.organization;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.rest.organization.OrganizationApi.ORGANIZATION_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.rest.organization.form.OrganizationBulkProcessingInput;
import io.openaev.rest.organization.form.OrganizationCreateInput;
import io.openaev.rest.organization.form.OrganizationUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Organizations deliberately remain on v1: v2 would mask unguarded primary-key lookups. */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=tags")
@DisplayName("Organization HTTP tenant isolation")
class OrganizationHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private InjectComposer injectComposer;

  private Tenant tenantA;
  private Tenant tenantB;
  private Organization organizationA;
  private Organization organizationB;
  private Organization defaultOrganization;
  private Tag tagA;
  private Tag tagB;
  private String searchPrefix;

  @BeforeEach
  void setUp() throws Exception {
    organizationComposer.reset();
    tagComposer.reset();
    injectComposer.reset();
    tenantA = tenantHelper.createTenant("organization-http-a");
    tenantB = tenantHelper.createTenant("organization-http-b");
    // Onboarding enrolls its creator; each test must grant only the memberships it exercises.
    String userId = testUserHolder.get().getId();
    for (Tenant tenant : List.of(tenantA, tenantB)) {
      tenantRepository.removeUserFromTenant(userId, tenant.getId());
      tenantMembershipCacheManager.evict(userId, tenant.getId());
    }
    assertThat(tenantMembershipCacheManager.findTenantIdsByUserId(userId)).isEmpty();
    searchPrefix = "organization-http-" + UUID.randomUUID();
    tagA = seedTag(tenantA);
    tagB = seedTag(tenantB);
    organizationA = seedOrganization(tenantA, "a", tagA);
    organizationB = seedOrganization(tenantB, "b", tagB);
    defaultOrganization = seedOrganization(new Tenant(DEFAULT_TENANT_UUID), "default", null);
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("ID-based ownership checks")
  class IdAccess {

    @ParameterizedTest
    @ValueSource(strings = {"plain", "header", "path"})
    @DisplayName("Foreign organization reads and inject searches are not found")
    void given_foreignOrganization_should_rejectReads(String route) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());

      // Act & Assert
      mvc.perform(scoped(get(baseUri(route) + "/" + organizationB.getId()), route))
          .andExpect(status().isNotFound());
      mvc.perform(
              scoped(post(baseUri(route) + "/" + organizationB.getId() + "/injects/search"), route)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(PaginationFixture.getDefault().build()))
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "header", "path"})
    @DisplayName("Foreign updates leave every stored attribute and tag unchanged")
    void given_foreignOrganization_should_rejectUpdate(String route) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      String before = rawState(organizationB.getId());

      // Act
      mvc.perform(
              scoped(put(baseUri(route) + "/" + organizationB.getId()), route)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput()))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawState(organizationB.getId())).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "header", "path"})
    @DisplayName("Foreign deletes leave the organization and its tags intact")
    void given_foreignOrganization_should_rejectDelete(String route) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      String before = rawState(organizationB.getId());

      // Act
      mvc.perform(scoped(delete(baseUri(route) + "/" + organizationB.getId()), route).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawState(organizationB.getId())).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "header", "path"})
    @DisplayName("Own-tenant CRUD keeps its existing response contract")
    void given_ownOrganization_should_allowReadUpdateAndDelete(String route) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      String uri = baseUri(route) + "/" + organizationA.getId();
      OrganizationUpdateInput input = updateInput();
      input.setTagIds(List.of(tagA.getId()));

      // Act & Assert
      mvc.perform(scoped(get(uri), route)).andExpect(status().isOk());
      response(
          scoped(post(uri + "/injects/search"), route)
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(PaginationFixture.getDefault().build()))
              .with(csrf()));
      String response =
          response(
              scoped(put(uri), route)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()));
      assertThatJson(response).node("organization_name").isEqualTo(input.getName());
      assertThatJson(response).node("organization_tags").isArray().containsExactly(tagA.getId());
      assertThatJson(response).node("tenant_id").isAbsent();
      assertThat(rawState(organizationA.getId())).contains(input.getName());
      mvc.perform(scoped(delete(uri), route).with(csrf())).andExpect(status().isOk());
      assertThat(rawState(organizationA.getId())).isNull();
      assertThat(rawState(organizationB.getId())).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"header", "path"})
    @DisplayName("A selector narrows even a multi-tenant administrator")
    void given_multiTenantAdminSelectingA_should_rejectB(String route) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      tenantHelper.attachCurrentUserToTenant(tenantB.getId());
      String before = rawState(organizationB.getId());

      // Act & Assert
      mvc.perform(scoped(get(baseUri(route) + "/" + organizationB.getId()), route))
          .andExpect(status().isNotFound());
      mvc.perform(
              scoped(put(baseUri(route) + "/" + organizationB.getId()), route)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput()))
                  .with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(scoped(delete(baseUri(route) + "/" + organizationB.getId()), route).with(csrf()))
          .andExpect(status().isNotFound());
      assertThat(rawState(organizationB.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("An unselected multi-tenant caller can mutate an authorized organization")
    void given_multiTenantAdminWithoutSelector_should_allowB() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      tenantHelper.attachCurrentUserToTenant(tenantB.getId());
      String uri = ORGANIZATION_URI + "/" + organizationB.getId();
      OrganizationUpdateInput input = updateInput();
      input.setTagIds(List.of(tagB.getId()));

      // Act & Assert
      mvc.perform(get(uri)).andExpect(status().isOk());
      response(
          put(uri)
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(input))
              .with(csrf()));
      assertThat(rawState(organizationB.getId())).contains(input.getName());
      mvc.perform(delete(uri).with(csrf())).andExpect(status().isOk());
      assertThat(rawState(organizationB.getId())).isNull();
    }

    @Test
    @DisplayName("The tenant path takes precedence over a contradictory header")
    void given_pathAAndHeaderB_should_keepScopeA() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      tenantHelper.attachCurrentUserToTenant(tenantB.getId());

      // Act & Assert
      mvc.perform(
              get(baseUri("path") + "/" + organizationA.getId())
                  .header(TENANT_IDS_HEADER, tenantB.getId()))
          .andExpect(status().isOk());
      mvc.perform(
              get(baseUri("path") + "/" + organizationB.getId())
                  .header(TENANT_IDS_HEADER, tenantB.getId()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Selectors cannot grant foreign membership")
    void given_foreignSelector_should_returnForbidden() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());

      // Act & Assert
      mvc.perform(
              get(ORGANIZATION_URI + "/" + organizationB.getId())
                  .header(TENANT_IDS_HEADER, tenantB.getId()))
          .andExpect(status().isForbidden());
      mvc.perform(get(tenantUriFor(tenantB) + "/" + organizationB.getId()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Missing memberships never grant access to the default tenant")
    void given_noMembership_should_rejectIdAccess() throws Exception {
      // Arrange
      String before = rawState(defaultOrganization.getId());
      String uri = ORGANIZATION_URI + "/" + defaultOrganization.getId();

      // Act & Assert
      mvc.perform(get(uri)).andExpect(status().isNotFound());
      mvc.perform(
              put(uri)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput()))
                  .with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(delete(uri).with(csrf())).andExpect(status().isNotFound());
      assertThat(rawState(defaultOrganization.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("Missing identifiers use the same response as inaccessible organizations")
    void given_missingOrganization_should_returnNotFound() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      String uri = ORGANIZATION_URI + "/" + UUID.randomUUID();

      // Act & Assert
      mvc.perform(get(uri)).andExpect(status().isNotFound());
      mvc.perform(
              put(uri)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput()))
                  .with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(delete(uri).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A preloaded foreign organization does not bypass the ownership guard")
    void given_foreignOrganizationInPersistenceContext_should_rejectUpdate() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      entityManager.find(Organization.class, organizationB.getId());
      String before = rawState(organizationB.getId());

      // Act
      mvc.perform(
              put(ORGANIZATION_URI + "/" + organizationB.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput()))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawState(organizationB.getId())).isEqualTo(before);
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Creation and association attribution")
  class Creation {

    @ParameterizedTest
    @ValueSource(strings = {"plain", "header", "path"})
    @DisplayName("Creates are attributed to the authorized tenant, not the ambient default")
    void given_singleTenantScope_should_attributeCreate(String route) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      OrganizationCreateInput input = createInput();
      input.setTagIds(List.of(tagA.getId()));

      // Act
      String result =
          response(
              scoped(post(baseUri(route)), route)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()));

      // Assert
      String id = JsonPath.read(result, "$.organization_id");
      assertThatJson(rawState(id)).node("row.tenant_id").isEqualTo(tenantA.getId());
      assertThatJson(result).node("organization_tags").isArray().containsExactly(tagA.getId());
      assertThatJson(result).node("tenant_id").isAbsent();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Ambiguous creation is refused")
    void given_multipleTenants_should_rejectAmbiguousCreate(boolean withHeader) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      tenantHelper.attachCurrentUserToTenant(tenantB.getId());
      var request = post(ORGANIZATION_URI);
      if (withHeader) {
        request.header(TENANT_IDS_HEADER, tenantA.getId() + "," + tenantB.getId());
      }

      // Act & Assert
      mvc.perform(
              request
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(createInput()))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("The default-tenant creation fallback remains authorized")
    void given_defaultAndOtherMembership_should_createInDefault() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());

      // Act
      String result =
          response(
              post(ORGANIZATION_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(createInput()))
                  .with(csrf()));

      // Assert
      String id = JsonPath.read(result, "$.organization_id");
      assertThatJson(rawState(id)).node("row.tenant_id").isEqualTo(DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName("Missing scope cannot attribute a new organization")
    void given_noMembership_should_rejectCreate() throws Exception {
      // Arrange
      OrganizationCreateInput input = createInput();

      // Act & Assert
      mvc.perform(
              post(ORGANIZATION_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Membership in both tenants does not permit a cross-tenant tag association")
    void given_foreignTag_should_rejectUpdateWithoutMutatingOrganization() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      tenantHelper.attachCurrentUserToTenant(tenantB.getId());
      String before = rawState(organizationA.getId());
      OrganizationUpdateInput input = updateInput();
      input.setTagIds(List.of(tagB.getId()));

      // Act
      mvc.perform(
              put(ORGANIZATION_URI + "/" + organizationA.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawState(organizationA.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("A create cannot attach a foreign tag")
    void given_foreignTag_should_rejectCreate() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      OrganizationCreateInput input = createInput();
      input.setTagIds(List.of(tagB.getId()));

      // Act & Assert
      mvc.perform(
              post(ORGANIZATION_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Collection, options and bulk containment")
  class Collections {

    @Test
    @DisplayName("Tenant-prefixed collections and options contain only owned organizations")
    void given_tenantPath_should_filterAllCollections() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());

      // Act & Assert
      for (var request : collectionRequests(baseUri("path"))) {
        String result = response(request);
        assertThat(result).contains(organizationA.getId());
        assertThat(result).doesNotContain(organizationB.getId(), defaultOrganization.getId());
      }
    }

    @Test
    @DisplayName("Raw organization projections exclude foreign all-team injects")
    void given_allTeamInjectsInBothTenants_should_listOnlyOwnedInjects() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Inject injectA = InjectFixture.getInjectWithAllTeams();
      injectA.setTenant(tenantA);
      Inject injectB = InjectFixture.getInjectWithAllTeams();
      injectB.setTenant(tenantB);
      injectComposer.forInject(injectA).persist();
      injectComposer.forInject(injectB).persist();
      entityManager.flush();
      entityManager.clear();

      // Act
      String result = response(get(baseUri("path")));

      // Assert
      assertThat(result).contains(organizationA.getId(), injectA.getId());
      assertThat(result).doesNotContain(organizationB.getId(), injectB.getId());
    }

    @Test
    @DisplayName("Unprefixed default-tenant collections remain available to authorized callers")
    void given_defaultMembership_should_keepDefaultCollectionsVisible() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);

      // Act & Assert
      for (var request : collectionRequests(ORGANIZATION_URI)) {
        String result = response(request);
        assertThat(result).contains(defaultOrganization.getId());
        assertThat(result).doesNotContain(organizationA.getId(), organizationB.getId());
      }
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "plain", "header"})
    @DisplayName("Unprefixed collections never expose an unauthorized ambient default tenant")
    void given_defaultOutsideScope_should_hideItInAllCollections(String route) throws Exception {
      // Arrange
      if (!route.equals("none")) {
        tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      }

      // Act & Assert
      for (var request : collectionRequests(ORGANIZATION_URI)) {
        String result = response(scoped(request, route));
        assertThat(result)
            .doesNotContain(
                organizationA.getId(), organizationB.getId(), defaultOrganization.getId());
      }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Bulk deletion excludes foreign organizations in both selection modes")
    void given_tenantPath_should_deleteOnlySelectedOwnedOrganizations(boolean selectAll)
        throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Organization ignored = seedOrganization(tenantA, "ignored", null);
      OrganizationBulkProcessingInput input = bulkInput(selectAll);
      if (!selectAll) {
        input.setOrganizationIdsToProcess(
            List.of(
                organizationA.getId(),
                organizationB.getId(),
                defaultOrganization.getId(),
                ignored.getId()));
      }
      input.setOrganizationIdsToIgnore(List.of(ignored.getId()));
      String foreignBefore = rawState(organizationB.getId());

      // Act
      String result =
          response(
              delete(baseUri("path"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()));

      // Assert
      assertThatJson(result).isArray().containsExactly(organizationA.getId());
      assertThat(rawState(organizationA.getId())).isNull();
      assertThat(rawState(ignored.getId())).isNotNull();
      assertThat(rawState(organizationB.getId())).isEqualTo(foreignBefore);
      assertThat(rawState(defaultOrganization.getId())).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Unprefixed bulk deletion cannot delete unauthorized default-tenant rows")
    void given_defaultOutsideScope_should_leaveBulkTargetsUntouched(boolean selectAll)
        throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      String before = rawState(defaultOrganization.getId());

      // Act
      String result =
          response(
              delete(ORGANIZATION_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(bulkInput(selectAll)))
                  .with(csrf()));

      // Assert
      assertThatJson(result).isArray().isEmpty();
      assertThat(rawState(defaultOrganization.getId())).isEqualTo(before);
      assertThat(rawState(organizationA.getId())).isNotNull();
      assertThat(rawState(organizationB.getId())).isNotNull();
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("Real tenant capabilities")
  class Permissions {

    @ParameterizedTest
    @ValueSource(strings = {"plain", "path"})
    @DisplayName("A tenant manager cannot update or delete foreign organizations")
    void given_tenantManager_should_enforceOwnershipAfterRbac(String route) throws Exception {
      // Arrange
      String ownTenantId = route.equals("plain") ? DEFAULT_TENANT_UUID : tenantA.getId();
      Organization ownOrganization = route.equals("plain") ? defaultOrganization : organizationA;
      tenantHelper.grantCapabilitiesInTenant(
          ownTenantId,
          Set.of(Capability.MANAGE_TENANT_SETTINGS, Capability.DELETE_TENANT_SETTINGS));
      testUserHolder.refreshSecurityContext();
      assertThat(testUserHolder.get().isAdmin()).isFalse();
      String before = rawState(organizationB.getId());

      // Act & Assert
      response(
          put(baseUri(route) + "/" + ownOrganization.getId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(updateInput()))
              .with(csrf()));
      mvc.perform(
              put(baseUri(route) + "/" + organizationB.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput()))
                  .with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(delete(baseUri(route) + "/" + organizationB.getId()).with(csrf()))
          .andExpect(status().isNotFound());
      assertThat(rawState(organizationB.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("Membership alone is insufficient for mutations")
    void given_noWriteCapabilities_should_rejectOwnOrganizationMutations() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      String before = rawState(organizationA.getId());

      // Act & Assert
      mvc.perform(
              put(baseUri("path") + "/" + organizationA.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput()))
                  .with(csrf()))
          .andExpect(status().isForbidden());
      mvc.perform(delete(baseUri("path") + "/" + organizationA.getId()).with(csrf()))
          .andExpect(status().isForbidden());
      assertThat(rawState(organizationA.getId())).isEqualTo(before);
    }
  }

  private Tag seedTag(Tenant tenant) {
    Tag tag = TagFixture.getTagWithText(UUID.randomUUID().toString());
    tag.setTenant(tenant);
    return tagComposer.forTag(tag).persist().get();
  }

  private Organization seedOrganization(Tenant tenant, String suffix, Tag tag) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setName(searchPrefix + "-" + suffix);
    organization.setTenant(tenant);
    if (tag != null) {
      organization.setTags(new HashSet<>(Set.of(tag)));
    }
    return organizationComposer.forOrganization(organization).persist().get();
  }

  private OrganizationCreateInput createInput() {
    Organization fixture = OrganizationFixture.createDefaultOrganisation();
    OrganizationCreateInput input = new OrganizationCreateInput();
    input.setName(fixture.getName());
    input.setDescription(fixture.getDescription());
    return input;
  }

  private OrganizationUpdateInput updateInput() {
    Organization fixture = OrganizationFixture.createDefaultOrganisation();
    OrganizationUpdateInput input = new OrganizationUpdateInput();
    input.setName(fixture.getName());
    input.setDescription(fixture.getDescription());
    return input;
  }

  private OrganizationBulkProcessingInput bulkInput(boolean selectAll) {
    OrganizationBulkProcessingInput input = new OrganizationBulkProcessingInput();
    if (selectAll) {
      input.setSearchPaginationInput(PaginationFixture.simpleTextSearch(searchPrefix));
    } else {
      input.setOrganizationIdsToProcess(
          List.of(organizationA.getId(), organizationB.getId(), defaultOrganization.getId()));
    }
    return input;
  }

  private List<MockHttpServletRequestBuilder> collectionRequests(String uri) {
    return List.of(
        get(uri),
        post(uri + "/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(PaginationFixture.simpleTextSearch(searchPrefix)))
            .with(csrf()),
        get(uri + "/options").param("searchText", searchPrefix),
        post(uri + "/options")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                asJsonString(
                    List.of(
                        organizationA.getId(), organizationB.getId(), defaultOrganization.getId())))
            .with(csrf()));
  }

  private String baseUri(String route) {
    return route.equals("path") ? tenantUriFor(tenantA) : ORGANIZATION_URI;
  }

  private String tenantUriFor(Tenant tenant) {
    return ORGANIZATION_URI.replace("/api/", "/api/tenants/" + tenant.getId() + "/");
  }

  private MockHttpServletRequestBuilder scoped(
      MockHttpServletRequestBuilder request, String route) {
    return route.equals("header") ? request.header(TENANT_IDS_HEADER, tenantA.getId()) : request;
  }

  private String response(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String rawState(String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT json_build_object('row', to_jsonb(org), 'tags', ARRAY("
                          + "SELECT tag_id FROM organizations_tags WHERE organization_id = org.organization_id"
                          + " ORDER BY tag_id))::text FROM organizations org WHERE organization_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
