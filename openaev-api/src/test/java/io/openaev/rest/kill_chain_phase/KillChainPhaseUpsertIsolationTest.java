package io.openaev.rest.kill_chain_phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upsert-side isolation for {@code kill_chain_phases}, split from {@link
 * KillChainPhaseHttpIsolationTest} because the endpoint is {@code Propagation.NOT_SUPPORTED}: it
 * suspends any ambient transaction so each service call can retry in a fresh one. Inside a
 * {@code @Transactional} test that suspension also hides the test's uncommitted tenant and user
 * rows, which makes the request fail authentication.
 *
 * <p>So this class is NOT {@code @Transactional}: everything it creates is committed, and both the
 * phases and the tenants are removed in {@link #cleanUp()}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=kill_chain_phases")
@WithMockUser(isAdmin = true)
@DisplayName("kill_chain_phases upsert attribution and per-tenant duplication")
class KillChainPhaseUpsertIsolationTest extends IntegrationTest {

  private static final String PHASES = "/api/kill_chain_phases";
  private static final String TENANT_PHASES = "/api/tenants/{tenantId}/kill_chain_phases";
  private static final String KILL_CHAIN = "kcp-upsert-iso";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProvisioningStyleWriter provisioningStyleWriter;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("kcp-ups-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("kcp-ups-b").getId();
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM kill_chain_phases WHERE phase_kill_chain_name = ?", KILL_CHAIN);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  @Test
  @DisplayName("an upsert under tenant A's path is attributed to tenant A")
  void upsertUnderTenantAIsAttributedToA() throws Exception {
    mvc.perform(
            post(TENANT_PHASES + "/upsert", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertBody("up-a", "up-a", "TA9915"))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals(
        1, countByExternalIdAndTenant("TA9915", tenantA), "the upserted phase must belong to A");
    assertEquals(0, countByExternalIdAndTenant("TA9915", tenantB), "tenant B must own no such row");
  }

  @Test
  @DisplayName("an upsert with no tenant selector is refused (a single-tenant scope is required)")
  void upsertWithoutSelectorIsRejected() throws Exception {
    mvc.perform(
            post(PHASES + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertBody("up-none", "up-none", "TA9916"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertEquals(0, countByExternalId("TA9916"), "no row must be written without a selector");
  }

  @Test
  @DisplayName("upserting the same natural key under A then B yields two distinct rows")
  void upsertSameNaturalKeyUnderTwoTenantsYieldsTwoRows() throws Exception {
    String body = upsertBody("shared-phase", "shared-short", "TA9920");
    mvc.perform(
            post(TENANT_PHASES + "/upsert", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post(TENANT_PHASES + "/upsert", tenantB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isOk());

    assertEquals(
        1, countByExternalIdAndTenant("TA9920", tenantA), "tenant A owns exactly one such row");
    assertEquals(
        1, countByExternalIdAndTenant("TA9920", tenantB), "tenant B owns exactly one such row");
    assertEquals(
        2,
        countByNaturalKey("shared-short"),
        "each tenant must own an independent row for the same natural key");
  }

  @Test
  @DisplayName("under tenant A's path: a second upsert of the same key reuses A's row")
  void upsertTwiceUnderTenantAReusesTheSameRow() throws Exception {
    String body = upsertBody("reused", "reused-short", "TA9925");
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post(TENANT_PHASES + "/upsert", tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().isOk());
    }
    assertEquals(
        1,
        countByNaturalKey("reused-short"),
        "the second upsert must reuse tenant A's row, not duplicate it");
  }

  @Test
  @DisplayName("a write with no explicit attribution is refused, never attributed from the context")
  void writeWithoutExplicitAttributionIsRefused() {
    // The entity carries no TenantBaseListener since activation: attribution is explicit, through
    // TenantWriteScopeResolver on the endpoints and the resolved write tenant in the importer. A
    // save that forgets it must fail loudly rather than silently land on TenantContext's tenant,
    // which would mask the missing attribution and put the row on whatever tenant happened to be
    // set.
    TenantContext.setCurrentTenant(tenantA);
    try {
      assertThrows(
          RuntimeException.class,
          () -> {
            provisioningStyleWriter.saveFreshPhase("TA-PROV");
            entityManager.flush();
          },
          "an unattributed write must be refused by the non-nullable tenant_id, not rescued by a"
              + " listener reading TenantContext");
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private static String upsertBody(String name, String shortName, String externalId) {
    return "{\"kill_chain_phases\":[{"
        + "\"phase_kill_chain_name\":\""
        + KILL_CHAIN
        + "\","
        + "\"phase_name\":\""
        + name
        + "\","
        + "\"phase_shortname\":\""
        + shortName
        + "\","
        + "\"phase_external_id\":\""
        + externalId
        + "\","
        + "\"phase_stix_id\":\"x-mitre-tactic--"
        + UUID.nameUUIDFromBytes((KILL_CHAIN + shortName).getBytes())
        + "\","
        + "\"phase_order\":1}]}";
  }

  // Ground truth through JdbcTemplate: raw JDBC never reaches the statement inspector, so these
  // counts see every tenant's rows regardless of scope.
  private int countByExternalIdAndTenant(String externalId, String tenantId) {
    return count(
        "SELECT count(*) FROM kill_chain_phases WHERE phase_external_id = ? AND tenant_id = ?",
        externalId,
        tenantId);
  }

  private int countByExternalId(String externalId) {
    return count("SELECT count(*) FROM kill_chain_phases WHERE phase_external_id = ?", externalId);
  }

  private int countByNaturalKey(String shortName) {
    return count(
        "SELECT count(*) FROM kill_chain_phases"
            + " WHERE phase_kill_chain_name = ? AND phase_shortname = ?",
        KILL_CHAIN,
        shortName);
  }

  private int count(String sql, Object... args) {
    Integer count = jdbc.queryForObject(sql, Integer.class, args);
    return count == null ? 0 : count;
  }

  /**
   * Mirrors the provisioning write shape: @Transactional, TenantContext only, no TxCtx anywhere.
   */
  public static class ProvisioningStyleWriter {
    private final KillChainPhaseRepository killChainPhaseRepository;

    public ProvisioningStyleWriter(KillChainPhaseRepository killChainPhaseRepository) {
      this.killChainPhaseRepository = killChainPhaseRepository;
    }

    @Transactional
    public void saveFreshPhase(String externalId) {
      KillChainPhase phase = new KillChainPhase();
      phase.setKillChainName(KILL_CHAIN);
      phase.setName(externalId);
      phase.setShortName(externalId);
      phase.setExternalId(externalId);
      killChainPhaseRepository.save(phase);
    }
  }

  @TestConfiguration
  static class ProvisioningStyleWriterFixture {
    @Bean
    ProvisioningStyleWriter provisioningStyleWriter(KillChainPhaseRepository repository) {
      return new ProvisioningStyleWriter(repository);
    }
  }
}
