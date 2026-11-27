package io.openaev.rest.attack_pattern;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.attack_pattern.AttackPatternApi.ATTACK_PATTERN_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code createAttackPattern} carries no tenant selector, so {@link
 * io.openaev.config.TenantWriteScopeResolver#tenantForWrite} refuses any request scope naming more
 * than one tenant before the parent id is ever resolved: a caller of two tenants who names both in
 * the header gets a 400, not a create. For a caller with no selector in the request, the resolver's
 * own fallback already pins the scope to a single tenant, so the plain {@code findById} the parent
 * relation still uses (see {@code DatabaseHelper#updateRelation}) resolves inside that one tenant
 * for every request that reaches it: a parent id of another tenant is already treated like an
 * unknown id, exactly as a cross-tenant one is required to be (see {@code
 * AttackPatternApi#createAttackPattern}, line 119-126). This is a green test proving that shape,
 * not a fix.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("createAttackPattern cannot reach an unscoped parent lookup across tenants")
class AttackPatternParentLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String otherTenant;

  @BeforeEach
  void seedBothTenants() throws Exception {
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("attack-pattern-parent-o").getId();
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "given a caller of two tenants when the header names both then the create is refused"
          + " before any parent lookup runs")
  void given_headerNamingBothTenants_should_refuseTheCreate() throws Exception {
    mvc.perform(
            post(ATTACK_PATTERN_URI)
                .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput(null)))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "given a caller of a single tenant when no selector is sent and the body names a parent of"
          + " another tenant then the create pins that tenant and treats the parent as unknown")
  void given_noSelector_should_pinDefaultTenantAndIgnoreTheForeignParent() throws Exception {
    // -- Arrange: the parent is seeded through the other tenant while the caller is still a
    // member of it, then that membership is dropped so the create below resolves a single-tenant
    // scope (the point of this test: no selector, exactly one authorized tenant).
    String foreignParentId = createAttackPatternInOtherTenant();
    tenantHelper.detachCurrentUserFromTenant(otherTenant);

    // -- Act --
    String response =
        mvc.perform(
                post(ATTACK_PATTERN_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput(foreignParentId)))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- Assert: created in the default tenant, with no parent linked --
    String attackPatternId = JsonPath.read(response, "$.attack_pattern_id");
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM attack_patterns WHERE attack_pattern_id = ?",
                attackPatternId))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(
            column(
                entityManager,
                "SELECT attack_pattern_parent FROM attack_patterns WHERE attack_pattern_id = ?",
                attackPatternId))
        .containsExactly((String) null);
  }

  // -- Helpers --

  private AttackPatternCreateInput createInput(String parentId) {
    AttackPatternCreateInput input = new AttackPatternCreateInput();
    input.setName("attack-pattern-parent-link-" + UUID.randomUUID());
    input.setExternalId("T-" + UUID.randomUUID());
    input.setKillChainPhasesIds(List.of());
    input.setParentId(parentId);
    return input;
  }

  /** Seeded through the other tenant's own prefixed route, so it is written there, not ambient. */
  private String createAttackPatternInOtherTenant() throws Exception {
    String response =
        mvc.perform(
                post(TENANT_PREFIX + "/attack_patterns", otherTenant)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput(null)))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.attack_pattern_id");
  }
}
