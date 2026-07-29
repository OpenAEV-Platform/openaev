package io.openaev.rest.asset.security_platforms;

import static io.openaev.rest.asset.security_platforms.SecurityPlatformApi.SECURITY_PLATFORM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Collector;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.CollectorComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression test for #7025: with {@code collectors} on v2 tenant isolation (the production
 * configuration since #6751, which the default test properties do NOT exercise), the security
 * platform endpoints must carry a {@link io.openaev.context.TxCtx} tenant scope and initialize the
 * lazy {@code collectors} association inside it. Without that, the association load is fail-closed
 * EMPTY (no error), {@code security_platform_collectors} always serializes as {@code []}, and the
 * UI unlocks update / delete on every collector-managed platform.
 *
 * <p>The collector-purge release path (the previous lifecycle work of #6956) is asserted under the
 * same production configuration so the fix cannot regress it.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=collectors")
@WithMockUser(isAdmin = true)
@DisplayName("Security platform endpoints carry the tenant scope with collectors v2-activated")
class SecurityPlatformCollectorsTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private CollectorRepository collectorRepository;

  private String platformId;
  private Collector collector;

  @BeforeEach
  void setUp() {
    securityPlatformComposer.reset();
    collectorComposer.reset();

    // The TxCtx scope is the caller's authorized tenants: the mock user must be a member of the
    // default tenant (where the collector lives) for the scope to cover it.
    String userId = testUserHolder.get().getId();
    tenantRepository.addUserToTenant(userId, Tenant.DEFAULT_TENANT_UUID);
    tenantMembershipCacheManager.evict(userId, Tenant.DEFAULT_TENANT_UUID);

    SecurityPlatform platform =
        SecurityPlatformFixture.createDefault(
            "ScopedManagedPlatform", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name());
    SecurityPlatformComposer.Composer platformWrapper =
        securityPlatformComposer.forSecurityPlatform(platform);
    collector = CollectorFixture.createDefaultCollector("collector_scoped_managing_platform");
    collectorComposer.forCollector(collector).withSecurityPlatform(platformWrapper).persist();
    entityManager.flush();
    entityManager.clear();

    platformId = platformWrapper.get().getId();
  }

  @Test
  @DisplayName("GET by id serializes the live collector link (the UI's read-only signal)")
  void getByIdSerializesTheLiveCollectorLink() throws Exception {
    // Without the TxCtx scope the collectors read is fail-closed empty and this list silently
    // serializes as [] - the #7025 production regression that unlocked every managed platform.
    mvc.perform(
            get(SECURITY_PLATFORM_URI + "/" + platformId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security_platform_collectors[0]").value(collector.getId()));
  }

  @Test
  @DisplayName("search serializes the live collector link on each result")
  void searchSerializesTheLiveCollectorLink() throws Exception {
    mvc.perform(
            post(SECURITY_PLATFORM_URI + "/search")
                .content(asJsonString(PaginationFixture.simpleTextSearch("ScopedManagedPlatform")))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].asset_id").value(platformId))
        .andExpect(
            jsonPath("$.content[0].security_platform_collectors[0]").value(collector.getId()));
  }

  @Test
  @DisplayName("a purged collector still releases the platform (previous lifecycle work intact)")
  void purgedCollectorStillReleasesThePlatform() throws Exception {
    // First scoped request: asserts the managed state and, as a side effect, writes the tenant
    // scope into this test transaction so the collectors delete below is not itself fail-closed
    // (the collectors table is tenant-active for writes too).
    mvc.perform(
            get(SECURITY_PLATFORM_URI + "/" + platformId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security_platform_collectors[0]").value(collector.getId()));

    collectorRepository.deleteByCollectorId(collector.getId());
    entityManager.flush();
    entityManager.clear();

    mvc.perform(
            get(SECURITY_PLATFORM_URI + "/" + platformId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security_platform_collectors").isEmpty());
  }
}
