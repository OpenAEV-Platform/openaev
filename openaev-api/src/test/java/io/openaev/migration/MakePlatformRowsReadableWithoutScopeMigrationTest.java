package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.utilstest.WithoutTenantScope;
import jakarta.persistence.FlushModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies, on the live schema, the SQL functions that decide which rows of a dual-scope table a
 * transaction reads and writes: a platform row is readable whatever the scope, a tenant row only
 * inside it, and a write that is not allowed raises.
 *
 * <p>{@code @Transactional} so every setting made here is transaction-local and rolls back.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithoutTenantScope
@DisplayName("can_access_tenant and require_tenant_write on a platform row")
class MakePlatformRowsReadableWithoutScopeMigrationTest extends IntegrationTest {

  private void scope(String tenants) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :tenants, true)")
        .setFlushMode(FlushModeType.COMMIT)
        .setParameter("tenants", tenants)
        .getSingleResult();
  }

  private void platformWrite() {
    entityManager
        .createNativeQuery("SELECT set_config('app.platform_write', 'true', true)")
        .setFlushMode(FlushModeType.COMMIT)
        .getSingleResult();
  }

  private boolean sql(String expression) {
    return (Boolean)
        entityManager
            .createNativeQuery("SELECT " + expression)
            .setFlushMode(FlushModeType.COMMIT)
            .getSingleResult();
  }

  @Nested
  @DisplayName("reads")
  class Reads {

    @Test
    @DisplayName("given_noScope_should_letAPlatformRowThroughOnADualScopeRead")
    void given_noScope_should_letAPlatformRowThroughOnADualScopeRead() {
      // Arrange
      scope("");

      // Act + Assert
      assertThat(sql("can_access_tenant(NULL, true)")).isTrue();
    }

    @Test
    @DisplayName("given_noScope_should_stillRefuseATenantRowAndAStrictTablesNullRow")
    void given_noScope_should_stillRefuseATenantRowAndAStrictTablesNullRow() {
      // Arrange
      scope("");

      // Act + Assert
      assertThat(sql("can_access_tenant('tenant-a', true)")).isFalse();
      assertThat(sql("can_access_tenant('tenant-a')")).isFalse();
      assertThat(sql("can_access_tenant(NULL)")).isFalse();
    }

    @Test
    @DisplayName("given_aScope_should_keepFilteringByTenant")
    void given_aScope_should_keepFilteringByTenant() {
      // Arrange
      scope("tenant-a,tenant-b");

      // Act + Assert
      assertThat(sql("can_access_tenant('tenant-b', true)")).isTrue();
      assertThat(sql("can_access_tenant('tenant-c', true)")).isFalse();
      assertThat(sql("can_access_tenant(NULL)")).isFalse();
      assertThat(sql("can_access_tenant(NULL, true)")).isTrue();
    }
  }

  @Nested
  @DisplayName("writes")
  class Writes {

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_raiseOnAPlatformRow")
    void given_noPlatformWriteMarker_should_raiseOnAPlatformRow() {
      // Arrange
      scope("tenant-a");

      // Act + Assert
      assertThatThrownBy(() -> sql("require_tenant_write(NULL, true)"))
          .hasStackTraceContaining("platform-write");
    }

    @Test
    @DisplayName("given_aTenantOutsideTheScope_should_raise")
    void given_aTenantOutsideTheScope_should_raise() {
      // Arrange
      scope("tenant-a");

      // Act + Assert
      assertThatThrownBy(() -> sql("require_tenant_write('tenant-b', true)"))
          .hasStackTraceContaining("outside the scope");
    }

    @Test
    @DisplayName("given_theMarkerOrTheScope_should_allowTheWrite")
    void given_theMarkerOrTheScope_should_allowTheWrite() {
      // Arrange
      scope("tenant-a");
      platformWrite();

      // Act + Assert
      assertThat(sql("require_tenant_write(NULL, true)")).isTrue();
      assertThat(sql("require_tenant_write('tenant-a', true)")).isTrue();
    }

    @Test
    @DisplayName("given_aCheckThatAnswersFalse_should_raiseInsteadOfWritingAPlatformRow")
    void given_aCheckThatAnswersFalse_should_raiseInsteadOfWritingAPlatformRow() {
      // Arrange: the check function is replaced inside this rolled-back transaction by one that
      // refuses without raising, the answer the real function never gives
      scope("tenant-a");
      entityManager
          .createNativeQuery(
              "CREATE OR REPLACE FUNCTION require_tenant_write(row_tenant_id text,"
                  + " allow_platform_write boolean DEFAULT false) RETURNS boolean LANGUAGE sql AS"
                  + " $$ SELECT false $$")
          .setFlushMode(FlushModeType.COMMIT)
          .executeUpdate();

      // Act + Assert
      assertThatThrownBy(() -> sql("tenant_write_value(NULL, true)"))
          .hasStackTraceContaining("write refused");
      assertThatThrownBy(() -> sql("tenant_write_value('tenant-a', true)"))
          .hasStackTraceContaining("write refused");
    }
  }
}
