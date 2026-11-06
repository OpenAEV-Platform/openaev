package io.openaev.rest.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.App;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * With {@code parameters} active, {@code App.init()} must survive being run a second time against a
 * database where the platform_instance row it seeds already exists - the shape of every restart.
 * The bootstrap reads that row under an explicit scope, so it finds it instead of inserting a
 * second one that would collide with {@code uk_parameters_key_platform}.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@code App.init()} opens its own transaction through
 * {@code TenantScopedTransaction#executeAsPlatformWrite}, which refuses to run inside an
 * already-active one (see that class's testing note and {@code
 * TenantScopedTransactionIntegrationTest}, the reference pattern this follows).
 */
@DisplayName("App.init() survives a restart with parameters active")
class DualScopeParametersBootTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private App app;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  /**
   * This test's own verification reads need a scope too: {@code findByKeyAndTenantIsNull} is a
   * plain repository call with no {@code TxCtx} argument, so {@code TenantScopeTransactionAspect}
   * leaves it untouched and, off any ambient scope, {@code can_access_tenant} fail-closes it. The
   * class is deliberately not {@code @Transactional} (see the class javadoc), so this opens its own
   * short top-level transaction rather than joining one.
   */
  private String platformInstanceId() {
    return tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () ->
            settingRepository
                .findByKeyAndTenantIsNull(SettingKeys.PLATFORM_INSTANCE.key())
                .orElseThrow()
                .getValue());
  }

  @Test
  @DisplayName(
      "given_parametersArmedAndTheInstanceRowAlreadyPresent_should_bootAgainWithoutCrashing")
  void given_parametersArmedAndTheInstanceRowAlreadyPresent_should_bootAgainWithoutCrashing() {
    // Arrange: the context already completed one real boot (App.init() ran as a @PostConstruct)
    // to reach this test, so the platform_instance row already exists.
    String firstBootId = platformInstanceId();

    // Act: re-run the startup sequence a restart runs, against that existing row. The scoped,
    // platform-write read finds the row instead of re-inserting it.
    app.init();

    // Assert: no exception, and the id is the same row, not a duplicate.
    assertEquals(firstBootId, platformInstanceId());
  }
}
