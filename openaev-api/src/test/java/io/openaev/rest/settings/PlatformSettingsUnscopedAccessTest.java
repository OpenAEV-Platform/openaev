package io.openaev.rest.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SettingRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.settings.SettingService;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Callers that read {@code parameters} with no scope: the licence cache refreshed outside any
 * transaction, the session limit read at login, the scheduled collectors. A platform row of a
 * dual-scope table is readable with no scope; a tenant row and any write are not.
 *
 * <p>Deliberately NOT {@code @Transactional}: every call below runs the way the background caller
 * runs, with no ambient transaction and therefore no {@code app.current_tenants} setting. Rows are
 * committed for real and restored afterwards.
 */
@WithoutTenantScope
@DisplayName("parameters read and written with no scope")
class PlatformSettingsUnscopedAccessTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_ONLY_KEY = "platform-read-test-tenant-only";

  @Autowired private SettingRepository settingRepository;
  @Autowired private SettingService settingService;
  @Autowired private EnterpriseEditionService enterpriseEditionService;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  /** Previous platform values of the keys a test touched, restored by {@link #restore()}. */
  private final java.util.Map<String, Optional<String>> previous = new java.util.LinkedHashMap<>();

  @AfterEach
  void restore() {
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          previous.forEach(
              (key, value) -> {
                Optional<Setting> current = settingRepository.findByKeyAndTenantIsNull(key);
                if (value.isPresent()) {
                  Setting setting = current.orElseGet(() -> new Setting(key, value.get()));
                  setting.setValue(value.get());
                  settingRepository.save(setting);
                } else {
                  current.ifPresent(settingRepository::delete);
                }
              });
          settingRepository
              .findByKeyAndTenantId(TENANT_ONLY_KEY, Tenant.DEFAULT_TENANT_UUID)
              .ifPresent(settingRepository::delete);
        });
    previous.clear();
  }

  private void putPlatformSetting(String key, String value) {
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          Optional<Setting> current = settingRepository.findByKeyAndTenantIsNull(key);
          previous.putIfAbsent(key, current.map(Setting::getValue));
          Setting setting = current.orElseGet(() -> new Setting(key, value));
          setting.setValue(value);
          settingRepository.save(setting);
        });
  }

  @Nested
  @DisplayName("reads")
  class Reads {

    @Test
    @DisplayName("given_noScope_should_readAPersistedPlatformIntSetting")
    void given_noScope_should_readAPersistedPlatformIntSetting() {
      // Arrange: the session limit an admin saved
      putPlatformSetting(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key(), "3");

      // Act: the read SessionManager.enforceSessionLimit makes at login, with no scope
      int limit = settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT);

      // Assert
      assertEquals(3, limit);
    }

    @Test
    @DisplayName("given_noScope_should_readThePersistedLicensePem")
    void given_noScope_should_readThePersistedLicensePem() {
      // Arrange
      putPlatformSetting(SettingKeys.PLATFORM_ENTERPRISE_LICENSE.key(), "stored-pem-value");

      // Act: the read the licence cache makes on a miss after the startup eviction, with no scope
      String pem = enterpriseEditionService.getEnterpriseEditionLicensePem();

      // Assert: a licence configured on the application wins over the stored one, but either way
      // the PEM is not empty
      assertTrue(!pem.isEmpty(), "the stored platform licence must be readable with no scope");
    }

    @Test
    @DisplayName("given_noScope_should_notReadATenantRow")
    void given_noScope_should_notReadATenantRow() {
      // Arrange: a tenant row
      tenantScopedTransaction.execute(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
          () -> {
            Setting tenantRow = new Setting(TENANT_ONLY_KEY, "tenant-value");
            tenantRow.setTenant(
                tenantRepository.findById(Tenant.DEFAULT_TENANT_UUID).orElseThrow());
            settingRepository.save(tenantRow);
          });

      // Act + Assert: a platform row carries no tenant data; a tenant row still needs its scope
      assertTrue(
          settingRepository
              .findByKeyAndTenantId(TENANT_ONLY_KEY, Tenant.DEFAULT_TENANT_UUID)
              .isEmpty());
      assertTrue(settingRepository.findAllByTenantId(Tenant.DEFAULT_TENANT_UUID).isEmpty());
    }

    @Test
    @DisplayName("given_noScope_should_listThePlatformRowsOnly")
    void given_noScope_should_listThePlatformRowsOnly() {
      // Act
      List<Setting> rows = settingRepository.findAllByTenantIsNull();

      // Assert
      assertTrue(!rows.isEmpty(), "the platform rows seeded at boot must be readable");
      assertTrue(rows.stream().allMatch(row -> row.getTenant() == null));
    }
  }

  @Nested
  @DisplayName("writes")
  class Writes {

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_refuseCreatingAPlatformRowWithAnError")
    void given_noPlatformWriteMarker_should_refuseCreatingAPlatformRowWithAnError() {
      // Arrange
      Setting attempt = new Setting("platform-read-test-unmarked-insert", "value");

      // Act + Assert: the refusal raises; it is never an insert of zero rows
      Throwable thrown = assertThrows(Throwable.class, () -> settingRepository.save(attempt));
      assertTrue(rootMessage(thrown).contains("platform-write"), rootMessage(thrown));
      assertTrue(
          settingRepository
              .findByKeyAndTenantIsNull("platform-read-test-unmarked-insert")
              .isEmpty());
    }

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_refuseDeletingAPlatformRowWithAnError")
    void given_noPlatformWriteMarker_should_refuseDeletingAPlatformRowWithAnError() {
      // Arrange
      putPlatformSetting(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key(), "5");

      // Act + Assert: the refusal is the platform-write one, not an unrelated flush failure
      Throwable thrown =
          assertThrows(
              Throwable.class,
              () ->
                  settingRepository.deleteByKeyIn(
                      List.of(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key())));
      assertTrue(rootMessage(thrown).contains("platform-write"), rootMessage(thrown));
      assertEquals(5, settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT));
    }

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_refuseABulkUpdateOfAPlatformRowWithAnError")
    void given_noPlatformWriteMarker_should_refuseABulkUpdateOfAPlatformRowWithAnError() {
      // Arrange
      putPlatformSetting(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key(), "5");

      // Act + Assert: a bulk write skips the entity row-count check, so the refusal must be raised
      // by the statement itself
      Throwable thrown =
          assertThrows(
              Throwable.class,
              () ->
                  tenantScopedTransaction.execute(
                      TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                      () ->
                          entityManager
                              .createQuery(
                                  "update Setting s set s.value = :value where s.key = :key")
                              .setParameter("value", "9")
                              .setParameter(
                                  "key", SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key())
                              .executeUpdate()));
      assertTrue(rootMessage(thrown).contains("platform-write"), rootMessage(thrown));
      assertEquals(5, settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT));
    }

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_refuseABulkDeleteOfAPlatformRowWithAnError")
    void given_noPlatformWriteMarker_should_refuseABulkDeleteOfAPlatformRowWithAnError() {
      // Arrange
      putPlatformSetting(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key(), "5");

      // Act + Assert
      Throwable thrown =
          assertThrows(
              Throwable.class,
              () ->
                  tenantScopedTransaction.execute(
                      TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                      () ->
                          entityManager
                              .createQuery("delete from Setting s where s.key = :key")
                              .setParameter(
                                  "key", SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key())
                              .executeUpdate()));
      assertTrue(rootMessage(thrown).contains("platform-write"), rootMessage(thrown));
      assertEquals(5, settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT));
    }

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_refuseANativeUpdateOfAPlatformRowWithAnError")
    void given_noPlatformWriteMarker_should_refuseANativeUpdateOfAPlatformRowWithAnError() {
      // Arrange
      putPlatformSetting(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key(), "5");

      // Act + Assert: a statement the architecture rule cannot read the table of is refused here
      Throwable thrown =
          assertThrows(
              Throwable.class,
              () ->
                  tenantScopedTransaction.execute(
                      TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                      () ->
                          entityManager
                              .createNativeQuery(
                                  "UPDATE parameters SET parameter_value = :value"
                                      + " WHERE parameter_key = :key")
                              .setParameter("value", "9")
                              .setParameter(
                                  "key", SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key())
                              .executeUpdate()));
      assertTrue(rootMessage(thrown).contains("platform-write"), rootMessage(thrown));
      assertEquals(5, settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT));
    }

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_refuseTheFlushOfAModifiedPlatformRow")
    void given_noPlatformWriteMarker_should_refuseTheFlushOfAModifiedPlatformRow() {
      // Arrange
      putPlatformSetting(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key(), "5");

      // Act + Assert: dirty checking writes a managed row with no save call anywhere
      Throwable thrown =
          assertThrows(
              Throwable.class,
              () ->
                  tenantScopedTransaction.execute(
                      TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                      () -> {
                        Setting managed =
                            settingRepository
                                .findByKeyAndTenantIsNull(
                                    SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key())
                                .orElseThrow();
                        managed.setValue("9");
                        entityManager.flush();
                      }));
      assertTrue(rootMessage(thrown).contains("platform-write"), rootMessage(thrown));
      assertEquals(5, settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT));
    }

    @Test
    @DisplayName("given_aJdbcUpdate_should_notBeSeenByThePlatformWriteRefusal")
    void given_aJdbcUpdate_should_notBeSeenByThePlatformWriteRefusal() {
      // Arrange
      putPlatformSetting(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key(), "5");

      // Act: the refusal is part of the statement rewrite done for Hibernate, so a statement sent
      // straight to JDBC is not refused. The architecture rule is the only guard of that shape
      // (PlatformRowWriters follows JdbcTemplate and Statement calls naming the table); if this
      // test turns red the database closed the gap and the rule's Javadoc must say so.
      tenantScopedTransaction.execute(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
          () ->
              jdbcTemplate.update(
                  "UPDATE parameters SET parameter_value = ? WHERE parameter_key = ?",
                  "9",
                  SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key()));

      // Assert
      assertEquals(9, settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT));
    }

    @Test
    @DisplayName("given_aPlatformWriteTransaction_should_applyABulkUpdateOfAPlatformRow")
    void given_aPlatformWriteTransaction_should_applyABulkUpdateOfAPlatformRow() {
      // Arrange
      putPlatformSetting(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key(), "5");

      // Act
      int updated =
          tenantScopedTransaction.executeAsPlatformWrite(
              TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
              () ->
                  entityManager
                      .createQuery("update Setting s set s.value = :value where s.key = :key")
                      .setParameter("value", "9")
                      .setParameter("key", SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT.key())
                      .executeUpdate());

      // Assert
      assertEquals(1, updated);
      assertEquals(9, settingService.getInt(SettingKeys.PLATFORM_SESSION_MAX_CONCURRENT));
    }
  }

  private static String rootMessage(Throwable thrown) {
    Throwable cursor = thrown;
    while (cursor.getCause() != null && cursor.getCause() != cursor) {
      cursor = cursor.getCause();
    }
    return String.valueOf(cursor.getMessage());
  }
}
