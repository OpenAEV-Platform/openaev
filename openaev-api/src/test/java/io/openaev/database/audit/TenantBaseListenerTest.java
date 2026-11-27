package io.openaev.database.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.Table;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Exercises the listener's @PrePersist logic directly, on a throwaway TenantBase, instead of
// through a real entity's repository: the listener's behavior does not depend on which entity
// carries it, and an entity can drop the listener in a later change.
@DisplayName("The tenant stamp of a tenant-scoped write")
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

  private final TenantBaseListener<ProbeTenantBase> listener = new TenantBaseListener<>();

  @Test
  @DisplayName("given no tenant context, should refuse the write")
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
  @DisplayName("given a tenant context, should stamp its tenant")
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
  @DisplayName("given a tenant on the entity, should leave it unchanged")
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
