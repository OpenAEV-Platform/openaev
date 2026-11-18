package io.openaev.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SettingRepository;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The platform-write marker belongs to the {@link PlatformWrite} method that sets it. A tenant
 * method that calls such a method inside its own transaction does not keep the marker once the
 * method returns, so its own later write cannot reach a platform row.
 *
 * <p>Deliberately NOT {@code @Transactional}: the methods below open their own transactions, which
 * is the nesting under test. Rows are committed for real and removed afterwards.
 */
@WithoutTenantScope
@Import(PlatformWriteMarkerScopeTest.Fixture.class)
@DisplayName("the platform-write marker is scoped to the method that sets it")
class PlatformWriteMarkerScopeTest extends IntegrationTest {

  private static final String NESTED_KEY = "marker-scope-test-written-by-the-nested-method";
  private static final String OUTER_KEY = "marker-scope-test-written-by-the-outer-method";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private Outer outer;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  @AfterEach
  void restore() {
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          settingRepository
              .findByKeyAndTenantIsNull(NESTED_KEY)
              .ifPresent(settingRepository::delete);
          settingRepository
              .findByKeyAndTenantIsNull(OUTER_KEY)
              .ifPresent(settingRepository::delete);
        });
  }

  @Test
  @DisplayName(
      "given_aTenantMethodCallingAPlatformWriteMethod_should_refuseItsOwnLaterPlatformWrite")
  void given_aTenantMethodCallingAPlatformWriteMethod_should_refuseItsOwnLaterPlatformWrite() {
    // Arrange
    TxCtx ctx = TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID);

    // Act: the outer method calls the nested platform write, then writes a platform row itself
    Throwable thrown = assertThrows(Throwable.class, () -> outer.callsThenWritesAPlatformRow(ctx));

    // Assert: the refusal is the platform-write one, and neither row exists
    Throwable cursor = thrown;
    while (cursor.getCause() != null && cursor.getCause() != cursor) {
      cursor = cursor.getCause();
    }
    assertTrue(String.valueOf(cursor.getMessage()).contains("platform-write"), cursor.getMessage());
    assertEquals(Optional.empty(), settingRepository.findByKeyAndTenantIsNull(OUTER_KEY));
    assertEquals(Optional.empty(), settingRepository.findByKeyAndTenantIsNull(NESTED_KEY));
  }

  @Test
  @DisplayName("given_aTenantMethodCallingAPlatformWriteMethod_should_keepTheNestedWrite")
  void given_aTenantMethodCallingAPlatformWriteMethod_should_keepTheNestedWrite() {
    // Arrange
    TxCtx ctx = TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID);

    // Act: the outer method only delegates the platform write
    outer.onlyCallsThePlatformWrite(ctx);

    // Assert
    assertTrue(settingRepository.findByKeyAndTenantIsNull(NESTED_KEY).isPresent());
  }

  /** A tenant method that nests a platform write. */
  public static class Outer {
    private final Nested nested;
    private final SettingRepository settingRepository;

    public Outer(Nested nested, SettingRepository settingRepository) {
      this.nested = nested;
      this.settingRepository = settingRepository;
    }

    @Transactional
    public void callsThenWritesAPlatformRow(TxCtx ctx) {
      nested.writeAPlatformRow(ctx, NESTED_KEY);
      settingRepository.save(new Setting(OUTER_KEY, "value"));
    }

    @Transactional
    public void onlyCallsThePlatformWrite(TxCtx ctx) {
      nested.writeAPlatformRow(ctx, NESTED_KEY);
    }
  }

  /** A platform write, the way a controller entry point declares one. */
  public static class Nested {
    private final SettingRepository settingRepository;

    public Nested(SettingRepository settingRepository) {
      this.settingRepository = settingRepository;
    }

    @Transactional
    @PlatformWrite
    public void writeAPlatformRow(TxCtx ctx, String key) {
      settingRepository.save(new Setting(key, "value"));
    }
  }

  @TestConfiguration
  static class Fixture {
    @Bean
    Nested nested(SettingRepository settingRepository) {
      return new Nested(settingRepository);
    }

    @Bean
    Outer outer(Nested nested, SettingRepository settingRepository) {
      return new Outer(nested, settingRepository);
    }
  }
}
