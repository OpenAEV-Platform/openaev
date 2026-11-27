package io.openaev.rest;

import static io.openaev.rest.finding.FindingApi.FINDING_URI;
import static io.openaev.rest.finding.FindingApi.TENANT_FINDING_URI;
import static io.openaev.rest.mapper.MapperApi.MAPPER_URI;
import static io.openaev.rest.user.MeApi.ME_URI;
import static io.openaev.rest.user.MeApi.TENANT_ME_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exception.TenantSingleScopeRequiredException;
import io.openaev.rest.mapper.form.ImportMapperAddInput;
import io.openaev.rest.scenario.form.InjectsImportTestInput;
import io.openaev.rest.user.form.me.UpdateProfileInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A read by id and the profile of the caller take their tenant from the request's scope, on both
 * routes: the path tenant on {@code /api/tenants/{tenantId}/...}, the {@code X-Tenant-Ids} header
 * on the non-prefixed route. The caller belongs to tenant B only, and the ambient tenant of the
 * test thread is cleared before each request, so it falls back to the default tenant: a read that
 * relies on it misses tenant B.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Reads by id and the profile take their tenant from the request scope")
class RequestTenantOfReadsAndProfileTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String HEADER_ROUTE = "header";
  private static final String PREFIXED_ROUTE = "prefixed";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private FindingComposer findingComposer;
  @Autowired private OrganizationComposer organizationComposer;

  private String tenantB;

  @BeforeEach
  void seedCallerOfTenantBOnly() throws Exception {
    findingComposer.reset();
    organizationComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("request-tenant-b").getId();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Finding by id")
  class FindingById {

    @ParameterizedTest(name = "{0} route")
    @ValueSource(strings = {HEADER_ROUTE, PREFIXED_ROUTE})
    @DisplayName("given a finding of tenant B should read it in tenant B")
    void given_findingOfTenantB_should_readItInTenantB(String route) throws Exception {
      // Arrange
      Finding finding = FindingFixture.createDefaultTextFindingWithRandomValue();
      finding.setTenant(new Tenant(tenantB));
      String findingId = findingComposer.forFinding(finding).persist().get().getId();
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response =
          mvc.perform(
                  onRoute(
                      route, get(uri(route, FINDING_URI, TENANT_FINDING_URI) + "/" + findingId)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).node("finding_id").isEqualTo(findingId);
    }

    @Test
    @DisplayName("given a finding of a tenant the caller is not a member of should not find it")
    void given_findingOfANonMemberTenant_should_notFindIt() throws Exception {
      // Arrange
      String findingId = persistFinding(Tenant.DEFAULT_TENANT_UUID, randomValue());
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(get(FINDING_URI + "/" + findingId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a header naming a tenant the caller is not a member of should refuse the read")
    void given_headerNamingANonMemberTenant_should_refuseTheRead() throws Exception {
      // Arrange
      String findingId = persistFinding(Tenant.DEFAULT_TENANT_UUID, randomValue());
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              get(FINDING_URI + "/" + findingId).header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Summary of a finding")
  class FindingSummary {

    @ParameterizedTest(name = "{0} route")
    @ValueSource(strings = {HEADER_ROUTE, PREFIXED_ROUTE})
    @DisplayName("given a value seen in tenant B and in the default tenant should count tenant B")
    void given_valueSeenInTwoTenants_should_countTheTenantOfTheFinding(String route)
        throws Exception {
      // Arrange
      String value = randomValue();
      String findingId = persistFinding(tenantB, value);
      persistFinding(tenantB, value);
      persistFinding(Tenant.DEFAULT_TENANT_UUID, value);
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response =
          mvc.perform(
                  onRoute(
                      route,
                      get(
                          uri(route, FINDING_URI, TENANT_FINDING_URI)
                              + "/"
                              + findingId
                              + "/summary")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).node("finding_occurrences").isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Profile of the caller")
  class Profile {

    @ParameterizedTest(name = "{0} route")
    @ValueSource(strings = {HEADER_ROUTE, PREFIXED_ROUTE})
    @DisplayName("given an organization of tenant B should link the caller to it in tenant B")
    void given_organizationOfTenantB_should_linkTheCallerInTenantB(String route) throws Exception {
      // Arrange
      Organization organization = OrganizationFixture.createDefaultOrganisation();
      organization.setName("request-tenant-organization-" + UUID.randomUUID());
      organization.setTenant(new Tenant(tenantB));
      String organizationId =
          organizationComposer.forOrganization(organization).persist().get().getId();
      UpdateProfileInput profile = new UpdateProfileInput();
      profile.setFirstname("Profile");
      profile.setLastname("TenantB");
      profile.setLang("en");
      profile.setTheme("dark");
      profile.setOrganizationId(organizationId);
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response = putProfileOnRoute(route, profile);

      // Assert
      assertThat(storedOrganizationIds(testUserHolder.get().getId()))
          .containsExactly(organizationId);
      assertThatJson(response).node("user_organization").isEqualTo(organizationId);
    }

    @ParameterizedTest(name = "{0} route")
    @ValueSource(strings = {HEADER_ROUTE, PREFIXED_ROUTE})
    @WithMockUser
    @DisplayName(
        "given a caller who is not an admin and an organization of tenant B should answer with it")
    void given_nonAdminCallerAndOrganizationOfTenantB_should_answerWithIt(String route)
        throws Exception {
      // Arrange
      String organizationId = persistOrganization(tenantB);
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response = putProfileOnRoute(route, profile("NotAdmin", organizationId));

      // Assert
      assertThatJson(response).node("user_admin").isEqualTo(false);
      assertThatJson(response).node("user_organization").isEqualTo(organizationId);
    }

    @ParameterizedTest(name = "{0} route")
    @ValueSource(strings = {HEADER_ROUTE, PREFIXED_ROUTE})
    @DisplayName(
        "given an organization of tenant B when the profile clears it should answer without an"
            + " organization")
    void given_organizationOfTenantB_when_profileClearsIt_should_answerWithoutOrganization(
        String route) throws Exception {
      // Arrange
      String organizationId = persistOrganization(tenantB);
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();
      String setResponse = putProfileOnRoute(route, profile("Cleared", organizationId));
      assertThatJson(setResponse).node("user_organization").isEqualTo(organizationId);
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response = putProfileOnRoute(route, profile("Cleared", null));

      // Assert
      assertThat(storedOrganizationIds(testUserHolder.get().getId())).isEmpty();
      assertThatJson(response).node("user_organization").isNull();
    }

    @Test
    @DisplayName(
        "given a caller of tenant B only and no selector should link the organization in tenant B")
    void given_callerOfTenantBOnlyAndNoSelector_should_linkTheOrganizationInTenantB()
        throws Exception {
      // Arrange
      String organizationId = persistOrganization(tenantB);
      UpdateProfileInput profile = profile("Single", organizationId);
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act
      mvc.perform(putProfile(profile)).andExpect(status().isOk());

      // Assert
      assertThat(storedOrganizationIds(testUserHolder.get().getId()))
          .containsExactly(organizationId);
    }

    @Test
    @DisplayName(
        "given a caller of several tenants without the default one and no selector should update"
            + " the name")
    void given_callerOfSeveralTenantsAndNoSelector_should_updateTheName() throws Exception {
      // Arrange
      tenantHelper.createTenantWithCurrentUser("request-tenant-c");
      UpdateProfileInput profile = profile("Several", null);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response =
          mvc.perform(putProfile(profile))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).node("user_firstname").isEqualTo("Several");
    }

    @Test
    @DisplayName(
        "given a caller of several tenants and no selector when an organization is set should"
            + " refuse the update")
    void given_callerOfSeveralTenantsAndNoSelector_when_organizationIsSet_should_refuse()
        throws Exception {
      // Arrange
      tenantHelper.createTenantWithCurrentUser("request-tenant-c");
      UpdateProfileInput profile = profile("Several", persistOrganization(tenantB));
      flushAndClear();
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(putProfile(profile)).andExpect(status().isBadRequest());
    }

    private String putProfileOnRoute(String route, UpdateProfileInput profile) throws Exception {
      return mvc.perform(
              onRoute(
                      route,
                      put(uri(route, ME_URI, TENANT_ME_URI) + "/profile")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(profile)))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    private MockHttpServletRequestBuilder putProfile(UpdateProfileInput profile) {
      return put(ME_URI + "/profile")
          .contentType(MediaType.APPLICATION_JSON)
          .content(asJsonString(profile))
          .with(csrf());
    }
  }

  @Nested
  @DisplayName("Dry run of an import mapper")
  class MapperDryRun {

    @Test
    @DisplayName("given a header naming two tenants should refuse the dry run")
    void given_headerNamingTwoTenants_should_refuseTheDryRun() throws Exception {
      // Arrange
      String tenantC = tenantHelper.createTenantWithCurrentUser("request-tenant-c").getId();
      ImportMapperAddInput mapper = new ImportMapperAddInput();
      mapper.setName("request-tenant-mapper");
      mapper.setInjectTypeColumn("A");
      InjectsImportTestInput input = new InjectsImportTestInput();
      input.setName("sheet");
      input.setTimezoneOffset(0);
      input.setImportMapper(mapper);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              post(MAPPER_URI + "/store/" + UUID.randomUUID())
                  .header(TENANT_HEADER, tenantB + "," + tenantC)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(TenantSingleScopeRequiredException.CODE));
    }
  }

  private String persistFinding(String tenantId, String value) {
    Finding finding = FindingFixture.createDefaultTextFinding();
    finding.setValue(value);
    finding.setTenant(new Tenant(tenantId));
    return findingComposer.forFinding(finding).persist().get().getId();
  }

  private static String randomValue() {
    return "request-tenant-finding-" + UUID.randomUUID();
  }

  private String persistOrganization(String tenantId) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setName("request-tenant-organization-" + UUID.randomUUID());
    organization.setTenant(new Tenant(tenantId));
    return organizationComposer.forOrganization(organization).persist().get().getId();
  }

  private static UpdateProfileInput profile(String firstname, String organizationId) {
    UpdateProfileInput profile = new UpdateProfileInput();
    profile.setFirstname(firstname);
    profile.setLastname("Caller");
    profile.setLang("en");
    profile.setTheme("dark");
    profile.setOrganizationId(organizationId);
    return profile;
  }

  private static void assertAmbientTenantIsTheDefaultOne() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private String uri(String route, String nonPrefixedUri, String prefixedUri) {
    return HEADER_ROUTE.equals(route) ? nonPrefixedUri : prefixedUri.replace("{tenantId}", tenantB);
  }

  private MockHttpServletRequestBuilder onRoute(
      String route, MockHttpServletRequestBuilder request) {
    return HEADER_ROUTE.equals(route) ? request.header(TENANT_HEADER, tenantB) : request;
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private List<String> storedOrganizationIds(String userId) {
    entityManager.flush();
    @SuppressWarnings("unchecked")
    List<String> ids =
        entityManager
            .createNativeQuery(
                "SELECT organization_id FROM users_organizations WHERE user_id = :id")
            .setParameter("id", userId)
            .getResultList();
    return ids;
  }
}
