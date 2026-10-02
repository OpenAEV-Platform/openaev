package io.openaev.database.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.Table;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Exercises the listener's @PrePersist logic directly, on a throwaway TenantBase, instead of
// through a real entity's repository: any entity's own table can be activated (removing the
// listener from that entity) or deactivated by a future PR, which would break a test pinned on
// it. The listener's behavior does not depend on which entity carries it.
class TenantBaseListenerTest {

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Table(name = "probe_tenant_bases")
  static class ProbeTenantBase implements TenantBase {

    private String id;
    private Tenant tenant;

    @Override
    public String getId() {
      return id;
    }

    @Override
    public void setId(String id) {
      this.id = id;
    }

    @Override
    public Tenant getTenant() {
      return tenant;
    }

    @Override
    public void setTenant(Tenant tenant) {
      this.tenant = tenant;
    }
  }

  private static void persist(
      TenantBaseListener<ProbeTenantBase> listener, ProbeTenantBase entity) {
    try {
      Method manageTenant =
          TenantBaseListener.class.getDeclaredMethod("manageTenant", TenantBase.class);
      manageTenant.setAccessible(true);
      manageTenant.invoke(listener, entity);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Nested
  @DisplayName("when the entity's table is v2-active")
  class WhenTheTableIsActive {

    private final TenantBaseListener<ProbeTenantBase> listener =
        new TenantBaseListener<>(List.of("probe_tenant_bases"));

    @Test
    void given_noTenantContext_should_throwOnPersist() {
      // Arrange
      TenantContext.clearCurrentTenant();
      ProbeTenantBase entity = new ProbeTenantBase();

      // Act & Assert
      assertThatThrownBy(() -> persist(listener, entity))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("unattributed tenant write: ProbeTenantBase");
    }

    @Test
    void given_tenantContextSet_should_stampTenantOnPersist() {
      // Arrange
      TenantContext.setCurrentTenant(Tenant.DEFAULT_TENANT_UUID);
      ProbeTenantBase entity = new ProbeTenantBase();

      // Act
      persist(listener, entity);

      // Assert
      assertThat(entity.getTenant()).isNotNull();
      assertThat(entity.getTenant().getId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    void given_tenantAlreadySetOnEntity_should_leaveItUnchanged() {
      // Arrange
      TenantContext.clearCurrentTenant();
      ProbeTenantBase entity = new ProbeTenantBase();
      Tenant explicitTenant = new Tenant("11111111-1111-1111-1111-111111111111");
      entity.setTenant(explicitTenant);

      // Act
      persist(listener, entity);

      // Assert
      assertThat(entity.getTenant()).isSameAs(explicitTenant);
    }
  }

  @Nested
  @DisplayName("when the entity's table is not v2-active")
  class WhenTheTableIsNotActive {

    private final TenantBaseListener<ProbeTenantBase> listener =
        new TenantBaseListener<>(List.of());

    @Test
    void given_noTenantContext_should_stampDefaultTenant() {
      // Arrange
      TenantContext.clearCurrentTenant();
      ProbeTenantBase entity = new ProbeTenantBase();

      // Act
      persist(listener, entity);

      // Assert
      assertThat(entity.getTenant()).isNotNull();
      assertThat(entity.getTenant().getId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }
  }
}
