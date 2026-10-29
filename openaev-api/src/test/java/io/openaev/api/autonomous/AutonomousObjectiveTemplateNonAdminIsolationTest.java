package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The gallery's isolation must not depend on the caller being an administrator. {@link
 * AutonomousObjectiveTemplateHttpIsolationTest} runs as admin, where RBAC is bypassed; this one
 * runs as a non-admin member of two tenants and shows the same result, because what isolates the
 * rows is the request scope and never the {@code isAdmin} flag.
 *
 * <p>The endpoint declares {@code skipRBAC = true}, so no capability gates it. The role granted
 * here is therefore empty on purpose: it exists only to give the user a real membership chain in
 * each tenant, which is what lets the selector resolve.
 *
 * <p>The gallery writes as well as reads, materialising the built-in catalog on first read, so this
 * also proves a non-admin's seed lands in its own tenant.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=autonomous_objective_templates")
@WithMockUser(isAdmin = false)
@DisplayName("autonomous objective template isolation holds for a non-admin spanning two tenants")
class AutonomousObjectiveTemplateNonAdminIsolationTest extends IntegrationTest {

  private static final String SCOPED_GALLERY =
      TENANT_PREFIX + "/autonomous-runs" + "/objective-templates";

  /** No capability gates the gallery; membership is the only thing this role is for. */
  private static final Set<Capability> NO_CAPABILITY = Set.of();

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // The endpoint is EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String customTemplateA;
  private String customTemplateB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneTemplateEach() throws Exception {
    // Arrange
    tenantA =
        tenantHelper
            .createTenantWithCapabilities("objective-tpl-nonadmin-a", NO_CAPABILITY)
            .getId();
    String tenantB =
        tenantHelper
            .createTenantWithCapabilities("objective-tpl-nonadmin-b", NO_CAPABILITY)
            .getId();
    customTemplateA = insertTemplate(tenantA, "nonadmin-custom-a");
    customTemplateB = insertTemplate(tenantB, "nonadmin-custom-b");
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("a non-admin listing the gallery under tenant A's path sees only A's templates")
  void given_aNonAdminOnTenantAPath_when_listingTheGallery_then_onlyTenantATemplatesComeBack()
      throws Exception {
    // Act
    String response =
        mvc.perform(get(SCOPED_GALLERY, tenantA).accept(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert: the positive case first, so an empty table cannot pass for a filtered one.
    assertThat(response)
        .as("tenant A's own template must appear for a non-admin member of A")
        .contains(customTemplateA);
    assertThat(response)
        .as("tenant B's template must not leak into tenant A's scope for a non-admin")
        .doesNotContain(customTemplateB);
  }

  /** Rows of another tenant are seeded with an explicit tenant_id, never the ambient one. */
  private String insertTemplate(String tenantId, String key) {
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
                + " 'environment', false, true, 10, now(), now())")
        .setParameter(1, id)
        .setParameter(2, tenantId)
        .setParameter(3, key)
        .setParameter(4, "label-" + key)
        .setParameter(5, "prompt-" + key)
        .executeUpdate();
    return id;
  }
}
