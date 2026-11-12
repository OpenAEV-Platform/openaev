package io.openaev.rest.organization;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.organization.OrganizationApi.ORGANIZATION_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
import java.util.HashSet;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The organization update refuses a tag of another tenant than the organization's.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants, and the lookup of the
 * tag ids then returns the tags of every tenant of the header. The links are read from {@code
 * organizations_tags} through JDBC on the test connection, which the statement inspector does not
 * rewrite. The class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Organization update refuses a tag of another tenant")
class OrganizationTagLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String ORGANIZATIONS_TAGS =
      "SELECT tag_id FROM organizations_tags WHERE organization_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String tagInB;
  private String tagInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("organization-tag-link-b").getId();
    tagInB = saveTag(tagRepository, tenantB);
    tagInDefault = saveTag(tagRepository, Tenant.DEFAULT_TENANT_UUID);
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
        "given an organization of B holding a tag of B when the body names a tag of each tenant"
            + " then the request is not found and the link stays")
    void given_organizationOfB_should_refuseATagOfAnotherTenant() throws Exception {
      // -- Arrange --
      String organization = saveOrganizationInB(tagInB);
      armRequest(organization);

      // -- Act --
      ResultActions result =
          putOnHeaderRoute(organization, organizationBody(List.of(tagInB, tagInDefault)));

      // -- Assert --
      result.andExpect(status().isNotFound());
      assertThat(column(entityManager, ORGANIZATIONS_TAGS, organization))
          .as("tags linked to the organization of B: kept %s, refused %s", tagInB, tagInDefault)
          .containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Body with a null tag list")
  class BodyWithNullTagList {

    @Test
    @DisplayName(
        "given an organization update with a null tag list on an organization of B holding a tag"
            + " of B then the request is refused and the link stays")
    void given_organizationUpdateWithNullTagList_should_refuseAndKeepTheLinks() throws Exception {
      // -- Arrange --
      String organization = saveOrganizationInB(tagInB);
      armRequest(organization);

      // -- Act --
      ResultActions result = putOnHeaderRoute(organization, organizationBody(null));

      // -- Assert --
      result.andExpect(status().isBadRequest());
      assertThat(column(entityManager, ORGANIZATIONS_TAGS, organization)).containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given an organization of B when the body under the path of B names a tag of B then the"
            + " tag is linked")
    void given_organizationOfB_should_linkTheTagOfBOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      String organization = saveOrganizationInB(null);
      armRequest(organization);

      // -- Act --
      mvc.perform(
              put(TENANT_PREFIX + "/organizations/{id}", tenantB, organization)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(organizationBody(List.of(tagInB))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, ORGANIZATIONS_TAGS, organization)).containsExactly(tagInB);
    }
  }

  // -- Helpers --

  private ResultActions putOnHeaderRoute(String organization, Map<String, Object> body)
      throws Exception {
    return mvc.perform(
        put(ORGANIZATION_URI + "/" + organization)
            .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))
            .with(csrf()));
  }

  private Map<String, Object> organizationBody(List<String> tags) {
    Map<String, Object> body = new HashMap<>();
    body.put("organization_name", "organization-tag-link-" + UUID.randomUUID());
    body.put("organization_tags", tags);
    return body;
  }

  private String saveOrganizationInB(String tagId) {
    Organization organization = OrganizationFixture.createOrganization();
    organization.setName("organization-tag-link-" + UUID.randomUUID());
    organization.setTenant(new Tenant(tenantB));
    if (tagId != null) {
      organization.setTags(
          new HashSet<>(tagRepository.findAllByIdInAndTenantId(List.of(tagId), tenantB)));
    }
    String id = organizationRepository.save(organization).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  /**
   * Each tag and the organization carry the tenant the scenario relies on, then the ambient tenant
   * is reset to the default one the request thread of the header route carries.
   */
  private void armRequest(String organization) {
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInB))
        .containsExactly(tenantB);
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM organizations WHERE organization_id = ?",
                organization))
        .containsExactly(tenantB);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
