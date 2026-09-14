package io.openaev.rest.asset;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Asset;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.rest.asset.ai_targets.form.AiTargetInput;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformInput;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformUpsertInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A security platform is a row of the {@code assets} table (SINGLE_TABLE with {@code Endpoint}), so
 * it needs the same explicit attribution: the tenant comes from the request scope, and an ambiguous
 * write is refused rather than resolved by the v1 thread-local.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@WithMockUser(isAdmin = true)
@DisplayName("every assets-table create carries its tenant explicitly")
class AssetWriteAttributionTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String tenantA;

  @BeforeEach
  void createTwoTenantsTheUserBelongsTo() throws Exception {
    // Two, so that a create without a selector is genuinely ambiguous. With a single membership
    // the resolver would legitimately resolve it and there would be nothing to assert.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantA = tenantHelper.createTenantWithCurrentUser("sp-a-" + UUID.randomUUID()).getId();
    tenantHelper.createTenantWithCurrentUser("sp-b-" + UUID.randomUUID());
  }

  @Test
  @DisplayName("a create under tenant A's path is stored with tenant A's id")
  void createUnderTenantPathIsAttributedToThatTenant() throws Exception {
    String body =
        mvc.perform(
                post("/api/tenants/{tenantId}/security_platforms", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input("attributed-platform")))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(body, "$.asset_id");
    assertEquals(
        tenantA,
        rawTenantOf(createdId),
        "the security platform must be stored under the tenant its request selected");
  }

  @Test
  @DisplayName("a create with no selector from a two-tenant caller is refused")
  void createWithoutSelectorIsRejected() throws Exception {
    mvc.perform(
            post("/api/security_platforms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input("no-selector-platform")))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an AI target create under tenant A's path is stored with tenant A's id")
  void aiTargetCreateUnderTenantPathIsAttributed() throws Exception {
    String body =
        mvc.perform(
                post("/api/tenants/{tenantId}/ai_targets", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(aiTargetInput("attributed-ai-target")))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(
        tenantA,
        rawTenantOf(JsonPath.read(body, "$.asset_id")),
        "the AI target must be stored under the tenant its request selected");
  }

  @Test
  @DisplayName("an AI target create with no selector from a two-tenant caller is refused")
  void aiTargetCreateWithoutSelectorIsRejected() throws Exception {
    // AI targets are the third discriminator writing to the assets table; the endpoint and
    // security platform paths are covered above and all three must fail the same way.
    mvc.perform(
            post("/api/ai_targets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(aiTargetInput("no-selector-ai-target")))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("the registration upsert falls back to the default tenant instead of refusing")
  void registrationRouteFallsBackWhereTheCreateRefuses() throws Exception {
    // D9: a tenant-unaware client (a collector registering itself) reaches the upsert route with no
    // selector. @RequireTenantSelector narrows a multi-tenant caller holding the default tenant to
    // that tenant instead of refusing, which is the platform's documented convention. The strict
    // half of the decision is createWithoutSelectorIsRejected above: same caller, same absence of
    // selector, UI route, refused. The two together are the decision. They cannot live in one test
    // method because the class is @Transactional and the scope aspect refuses a second request that
    // widens an already-fixed transaction scope.
    SecurityPlatformUpsertInput upsert = new SecurityPlatformUpsertInput();
    upsert.setName("registered-platform-" + UUID.randomUUID());
    upsert.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
    upsert.setExternalReference("ext-" + UUID.randomUUID());

    String body =
        mvc.perform(
                post("/api/security_platforms/upsert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(upsert))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(
        Tenant.DEFAULT_TENANT_UUID,
        rawTenantOf(JsonPath.read(body, "$.asset_id")),
        "an unscoped registration must land in the default tenant, not be refused");
  }

  private AiTargetInput aiTargetInput(String name) {
    AiTargetInput in = new AiTargetInput();
    in.setName(name + "-" + UUID.randomUUID());
    // ai_target_provider is @NotNull: without it the refusal under test would be a validation 400
    // rather than a scope 400, and the test would pass for the wrong reason.
    in.setAiTargetProvider(Asset.AI_TARGET_PROVIDER.OPENAI_COMPATIBLE);
    in.setAiTargetModality(Asset.AI_TARGET_MODALITY.TEXT);
    return in;
  }

  private SecurityPlatformInput input(String name) {
    SecurityPlatformInput in = new SecurityPlatformInput();
    in.setName(name + "-" + UUID.randomUUID());
    in.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
    return in;
  }

  /** Ground truth by raw JDBC: createNativeQuery would be scoped by the inspector like any read. */
  private String rawTenantOf(String assetId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement st =
                  connection.prepareStatement("SELECT tenant_id FROM assets WHERE asset_id = ?")) {
                st.setString(1, assetId);
                try (ResultSet rs = st.executeQuery()) {
                  return rs.next() ? rs.getString(1) : null;
                }
              }
            });
  }
}
