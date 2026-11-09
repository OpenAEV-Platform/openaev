package io.openaev.rest.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.SettingRepository;
import io.openaev.rest.settings.form.SettingsSessionsUpdateInput;
import io.openaev.rest.settings.form.TenantSettingsUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code parameters} table (entity {@link Setting}) is active with the platform-write rule.
 * Covers the anonymous public settings read and the write rule itself (a platform admin may write a
 * platform row, a tenant-scoped transaction may not, a tenant admin's own write is unaffected and
 * stays isolated to its tenant).
 *
 * <p>{@code TenantSettingsApi} (the tenant-settings write under test here) carries only the
 * tenant-prefixed route ({@code TENANT_PREFIX + "/tenant-settings"}, {@code
 * io.openaev.config.TenantUriUtils}): unlike a dual-mounted resource (e.g. {@code
 * TenantGroupApi}/{@code PlatformGroupApi}), there is no non-prefixed alias to also exercise here,
 * by the multi-tenancy convention that tenant-scoped data is never also mapped under a bare {@code
 * /api/...} route. The X-Tenant-Ids header path is exercised instead on {@code
 * PlatformSettingsApi.settings()}, which resolves its scope the same way every other
 * non-tenant-prefixed, authenticated endpoint does.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("parameters active, dual-scope write rule")
class DualScopeParametersActivationTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private final ObjectMapper mapper = new ObjectMapper();

  @Nested
  @DisplayName("anonymous public settings")
  class PublicSettings {

    @Test
    @DisplayName("given_noAuthentication_should_stillReturnThePlatformSettings")
    void given_noAuthentication_should_stillReturnThePlatformSettings() throws Exception {
      // Act + Assert: publicSettings() resolves the anonymous, empty scope (TxCtx.missing()), and
      // a platform row (tenant_id IS NULL) is readable under it.
      mvc.perform(get("/api/settings/public").with(csrf()).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.platform_run_mode").exists());
    }
  }

  @Nested
  @DisplayName("platform write")
  class PlatformWriteTests {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_platformAdmin_should_persistAPlatformSetting")
    void given_platformAdmin_should_persistAPlatformSetting() throws Exception {
      // Arrange
      SettingsSessionsUpdateInput input = new SettingsSessionsUpdateInput();
      input.setPlatformSessionMaxConcurrent(7);

      // Act
      mvc.perform(
              put("/api/settings/sessions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(input))
                  .with(csrf()))
          // Assert: the @PlatformWrite-marked transaction's write to the tenant_id IS NULL row
          // succeeds.
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.platform_session_max_concurrent").value(7));
      entityManager.clear();
      Setting persisted =
          settingRepository
              .findByKeyAndTenantIsNull(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key())
              .orElseThrow();
      assertEquals("7", persisted.getValue());
    }

    @Test
    @WithMockUser(autoJoinDefaultTenant = true)
    @DisplayName(
        "given_aTenantScopedTransactionWithNoPlatformWriteMarker_should_refuseUpdatingAPlatformRow")
    void
        given_aTenantScopedTransactionWithNoPlatformWriteMarker_should_refuseUpdatingAPlatformRow() {
      // Arrange: a platform row already exists (seeded at boot), and this transaction carries only
      // a tenant scope, never platform-write - the shape of every tenant-route transaction.
      tenantScopedTransaction.setScopeOnCurrentTransaction(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));
      Setting existing =
          settingRepository
              .findByKeyAndTenantIsNull(SettingKeys.PLATFORM_INSTANCE.key())
              .orElseThrow();
      existing.setValue("written-by-a-tenant-route");

      // Act + Assert: the UPDATE of a platform row with no app.platform_write raises, instead of
      // matching zero rows and surfacing as a row-count mismatch of the entity write
      PersistenceException thrown =
          assertThrows(
              PersistenceException.class,
              () -> {
                settingRepository.save(existing);
                entityManager.flush();
              });
      Throwable cause = thrown;
      while (cause.getCause() != null && cause.getCause() != cause) {
        cause = cause.getCause();
      }
      assertTrue(String.valueOf(cause.getMessage()).contains("platform-write"), cause.getMessage());
    }

    @Test
    @WithMockUser(autoJoinDefaultTenant = true)
    @DisplayName(
        "given_aTenantScopedTransactionWithNoPlatformWriteMarker_should_refuseCreatingAPlatformRow")
    void
        given_aTenantScopedTransactionWithNoPlatformWriteMarker_should_refuseCreatingAPlatformRow() {
      // Arrange
      tenantScopedTransaction.setScopeOnCurrentTransaction(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));
      Setting attempt = new Setting("dsp-tenant-route-cannot-create-a-platform-row", "value");

      // Act + Assert: a plain INSERT ... VALUES of a platform row raises; the caller is never told
      // a write succeeded when no row was written.
      Throwable thrown =
          assertThrows(
              Throwable.class,
              () -> {
                settingRepository.save(attempt);
                entityManager.flush();
              });
      assertTrue(rootMessage(thrown).contains("not platform-write"), rootMessage(thrown));
    }

    @Test
    @WithMockUser(autoJoinDefaultTenant = true)
    @DisplayName("given_aTenantRowOfAnotherTenant_should_refuseCreatingIt")
    void given_aTenantRowOfAnotherTenant_should_refuseCreatingIt() throws Exception {
      // Arrange: the transaction is scoped to the default tenant, the row names another one
      Tenant other = tenantHelper.createTenantWithCurrentUser("dsp-other-tenant-row");
      tenantScopedTransaction.setScopeOnCurrentTransaction(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));
      Setting attempt = new Setting("dsp-row-of-another-tenant", "value");
      attempt.setTenant(other);

      // Act + Assert
      Throwable thrown =
          assertThrows(
              Throwable.class,
              () -> {
                settingRepository.save(attempt);
                entityManager.flush();
              });
      assertTrue(rootMessage(thrown).contains("outside the scope"), rootMessage(thrown));
    }
  }

  private static String rootMessage(Throwable thrown) {
    Throwable cursor = thrown;
    while (cursor.getCause() != null && cursor.getCause() != cursor) {
      cursor = cursor.getCause();
    }
    return String.valueOf(cursor.getMessage());
  }

  @Nested
  @DisplayName("tenant write")
  class TenantWriteTests {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_tenantAdmin_should_persistItsOwnTenantSetting")
    void given_tenantAdmin_should_persistItsOwnTenantSetting() throws Exception {
      // Arrange
      Tenant tenant = tenantHelper.createTenantWithCurrentUser("dsp-tenant-write");
      TenantSettingsUpdateInput input =
          new TenantSettingsUpdateInput("Dual scope tenant name", "dark", "en", "", "", "");

      // Act
      mvc.perform(
              put("/api/tenants/" + tenant.getId() + "/tenant-settings")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(input)))
          .andExpect(status().isOk());

      // Assert: a real, non-null tenant_id row: the write rule does not touch a tenant row's write
      // path.
      entityManager.clear();
      Setting persisted =
          settingRepository
              .findByKeyAndTenantId(TenantSettingKeys.PLATFORM_NAME.key(), tenant.getId())
              .orElseThrow();
      assertEquals("Dual scope tenant name", persisted.getValue());
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_twoTenants_should_keepEachTenantsSettingToItself")
    void given_twoTenants_should_keepEachTenantsSettingToItself() throws Exception {
      // Arrange
      Tenant tenantA = tenantHelper.createTenantWithCurrentUser("dsp-tenant-a-isolation");
      Tenant tenantB = tenantHelper.createTenantWithCurrentUser("dsp-tenant-b-isolation");
      TenantSettingsUpdateInput inputA =
          new TenantSettingsUpdateInput("Only tenant A", "dark", "en", "", "", "");
      mvc.perform(
              put("/api/tenants/" + tenantA.getId() + "/tenant-settings")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(inputA)))
          .andExpect(status().isOk());

      // Act + Assert: tenant B's read falls back to the platform default, never tenant A's row -
      // can_access_tenant scopes findByKeyAndTenantId(key, tenantB) away from tenant A's row.
      mvc.perform(
              get("/api/tenants/" + tenantB.getId() + "/tenant-settings")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.platform_name").value(org.hamcrest.Matchers.not("Only tenant A")));
      assertNotEquals(
          "Only tenant A",
          settingRepository
              .findByKeyAndTenantId(TenantSettingKeys.PLATFORM_NAME.key(), tenantB.getId())
              .map(Setting::getValue)
              .orElse(null));
    }
  }
}
