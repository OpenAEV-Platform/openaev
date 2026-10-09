package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolation of {@code autonomous_objective_templates} through the real objective-template gallery
 * endpoint, on both routes (the tenant path and the {@code X-Tenant-Ids} header) and for both reads
 * and writes.
 *
 * <p>The gallery is the only endpoint on this table, and it both reads and writes: it materialises
 * the built-in catalog for the calling tenant on first read, then lists what that tenant has. So
 * the read assertions and the write-attribution assertions share a single request, and each test
 * stays on one tenant selection because the transaction aspect refuses to redefine a scope already
 * set in the same transaction.
 *
 * <p>Before this table was activated the endpoint returned every tenant's templates to any
 * authenticated caller, and the lazy seed decided "already seeded" from a cross-tenant lookup, so
 * the second tenant to open the gallery was served the first tenant's catalog and never got its
 * own. Both are pinned here: every cross-tenant assertion below goes red when the table is removed
 * from the armed list.
 *
 * <p>Rows belonging to another tenant are seeded with a raw native INSERT carrying an explicit
 * tenant_id, never through the ambient tenant.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=autonomous_objective_templates")
@WithMockUser(isAdmin = true)
@DisplayName("autonomous objective template isolation through the real gallery endpoint")
class AutonomousObjectiveTemplateHttpIsolationTest extends IntegrationTest {

  // Derived from the controller's own constants: a renamed route must break this test rather than
  // silently stop covering it.
  private static final String GALLERY_PATH = "/objective-templates";
  private static final String SCOPED_GALLERY = TENANT_PREFIX + "/autonomous-runs" + GALLERY_PATH;
  private static final String PLAIN_GALLERY = AutonomousRunApi.AUTONOMOUS_URI + GALLERY_PATH;

  /** One of the built-in keys the gallery seeds; stable across releases. */
  private static final String BUILTIN_KEY = "reach-domain-controller";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // The endpoint is EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;
  private String customTemplateA;
  private String customTemplateB;

  @BeforeEach
  void seedTwoTenantsWithOneCustomTemplateEach() throws Exception {
    // Arrange: two tenants the mock user belongs to, each with one admin-created template.
    tenantA = tenantHelper.createTenantWithCurrentUser("objective-tpl-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("objective-tpl-iso-b").getId();
    customTemplateA = seedTemplate(tenantA, "iso-custom-a");
    customTemplateB = seedTemplate(tenantB, "iso-custom-b");
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("reads, on both routes")
  class Reads {

    @Test
    @DisplayName(
        "given tenant A's path, when listing the gallery, then only A's templates come back")
    void given_tenant_a_path_when_listing_then_only_tenant_a_templates_come_back()
        throws Exception {
      // Act
      String response = listUnderPath(tenantA);

      // Assert: the positive case first, so an empty table cannot pass for a filtered one.
      assertThat(response)
          .as("tenant A's own template must appear under tenant A's path")
          .contains(customTemplateA);
      assertThat(response)
          .as("tenant B's template must not appear under tenant A's path")
          .doesNotContain(customTemplateB);
    }

    @Test
    @DisplayName(
        "given the X-Tenant-Ids header naming tenant B, when listing, then A's templates are absent")
    void given_the_header_route_when_listing_for_tenant_b_then_tenant_a_templates_are_absent()
        throws Exception {
      // Arrange: production reaches the non-prefixed route with an empty thread-local, which the
      // fixtures do not leave behind, so clear it and pin that before asserting anything.
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.hasCurrentTenant())
          .as("the header route must run with no ambient tenant, as it does in production")
          .isFalse();

      // Act
      String response = listUnderHeader(tenantB);

      // Assert
      assertThat(response)
          .as("tenant B's own template must appear for the header tenant")
          .contains(customTemplateB);
      assertThat(response)
          .as("tenant A's template must not appear for the header tenant")
          .doesNotContain(customTemplateA);
    }

    @Test
    @DisplayName(
        "given tenant A already holds the built-in catalog, when tenant B opens the gallery, then B"
            + " gets its own catalog instead of A's")
    void given_tenant_a_already_seeded_when_tenant_b_opens_the_gallery_then_b_gets_its_own()
        throws Exception {
      // Arrange: tenant A already holds the built-in, tenant B holds none of it.
      String builtinInA = seedBuiltin(tenantA, BUILTIN_KEY);
      assertThat(countByTenantAndKey(tenantB, BUILTIN_KEY))
          .as("tenant B must start without the built-in, otherwise the seed is not exercised")
          .isZero();

      // Act
      String response = listUnderPath(tenantB);

      // Assert: B now owns a row for that key, and A's row is not what B was served.
      assertThat(countByTenantAndKey(tenantB, BUILTIN_KEY))
          .as("tenant B must get its own built-in row rather than inheriting tenant A's")
          .isEqualTo(1L);
      assertThat(response)
          .as("the gallery must list the built-in for tenant B")
          .contains(BUILTIN_KEY);
      assertThat(response)
          .as("tenant A's built-in row must never be served to tenant B")
          .doesNotContain(builtinInA);
    }
  }

  @Nested
  @DisplayName("write attribution, on both routes")
  class Writes {

    @Test
    @DisplayName("given tenant A's path, when the gallery seeds a built-in, then it belongs to A")
    void given_tenant_a_path_when_the_gallery_seeds_a_builtin_then_it_belongs_to_tenant_a()
        throws Exception {
      // Act
      listUnderPath(tenantA);

      // Assert: read the stamped column back, not the entity.
      assertThat(tenantIdsHoldingKey(BUILTIN_KEY))
          .as("a built-in seeded under tenant A's path must be stamped with tenant A and no other")
          .containsExactly(tenantA);
    }

    @Test
    @DisplayName(
        "given the X-Tenant-Ids header naming tenant B, when the gallery seeds a built-in, then it"
            + " belongs to B and not to the ambient tenant")
    void given_the_header_route_when_the_gallery_seeds_a_builtin_then_it_belongs_to_header_tenant()
        throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.hasCurrentTenant())
          .as("the header route must run with no ambient tenant, as it does in production")
          .isFalse();

      // Act
      listUnderHeader(tenantB);

      // Assert: the write follows the header scope, not the default-tenant fallback the v1 listener
      // stamped on this route.
      assertThat(tenantIdsHoldingKey(BUILTIN_KEY))
          .as("a built-in seeded on the header route must be stamped with the header tenant only")
          .containsExactly(tenantB);
    }

    @Test
    @DisplayName(
        "given no tenant selector, when a caller holding the default tenant opens the gallery, then"
            + " the seed lands in the default tenant")
    void given_no_selector_when_the_caller_holds_the_default_tenant_then_it_seeds_the_default()
        throws Exception {
      // Arrange: production attaches every user to the default tenant, which is what makes the
      // single-tenant fallback available to a multi-tenant caller.
      tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
      TenantContext.clearCurrentTenant();

      // Act
      mvc.perform(get(PLAIN_GALLERY).accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isOk());

      // Assert: the gallery is a read endpoint, so a caller in several tenants must not be refused;
      // the selector falls back to the default tenant, which is where it seeds.
      assertThat(tenantIdsHoldingKey(BUILTIN_KEY))
          .as("the no-selector seed must land in the default tenant and nowhere else")
          .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName(
        "given no tenant selector and a caller in several tenants but not the default one, when"
            + " opening the gallery, then the request is refused rather than seeding a guess")
    void given_no_selector_and_no_default_tenant_membership_then_the_gallery_is_refused()
        throws Exception {
      // Arrange: the mock user holds tenants A and B only, so no single tenant can be inferred.
      TenantContext.clearCurrentTenant();

      // Act + Assert
      mvc.perform(get(PLAIN_GALLERY).accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isBadRequest());
      assertThat(tenantIdsHoldingKey(BUILTIN_KEY))
          .as("a refused request must not have seeded anything")
          .isEmpty();
    }
  }

  private String listUnderPath(String tenantId) throws Exception {
    return mvc.perform(
            get(SCOPED_GALLERY, tenantId).accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String listUnderHeader(String tenantId) throws Exception {
    return mvc.perform(
            get(PLAIN_GALLERY)
                .header("X-Tenant-Ids", tenantId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long countByTenantAndKey(String tenantId, String key) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT count(*) FROM autonomous_objective_templates WHERE tenant_id = ? AND"
                          + " autonomous_objective_template_key = ?")) {
                stmt.setString(1, tenantId);
                stmt.setString(2, key);
                try (var rows = stmt.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  /** Every tenant_id stamped on a row with this key, read straight off the column. */
  private List<String> tenantIdsHoldingKey(String key) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM autonomous_objective_templates WHERE"
                          + " autonomous_objective_template_key = ? ORDER BY tenant_id")) {
                stmt.setString(1, key);
                try (var rows = stmt.executeQuery()) {
                  List<String> tenants = new ArrayList<>();
                  while (rows.next()) {
                    tenants.add(rows.getString(1));
                  }
                  return tenants;
                }
              }
            });
  }

  private String seedTemplate(String tenantId, String key) {
    return insertTemplate(tenantId, key, false);
  }

  private String seedBuiltin(String tenantId, String key) {
    return insertTemplate(tenantId, key, true);
  }

  private String insertTemplate(String tenantId, String key, boolean builtin) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_objective_templates (autonomous_objective_template_id,"
                + " tenant_id, autonomous_objective_template_key,"
                + " autonomous_objective_template_label, autonomous_objective_template_prompt,"
                + " autonomous_objective_template_scope_mode,"
                + " autonomous_objective_template_builtin, autonomous_objective_template_enabled,"
                + " autonomous_objective_template_order, autonomous_objective_template_created_at,"
                + " autonomous_objective_template_updated_at) VALUES (?1, ?2, ?3, ?4, ?5,"
                + " 'environment', ?6, true, 10, now(), now())")
        .setParameter(1, id)
        .setParameter(2, tenantId)
        .setParameter(3, key)
        .setParameter(4, "label-" + key)
        .setParameter(5, "prompt-" + key)
        .setParameter(6, builtin)
        .executeUpdate();
    return id;
  }
}
