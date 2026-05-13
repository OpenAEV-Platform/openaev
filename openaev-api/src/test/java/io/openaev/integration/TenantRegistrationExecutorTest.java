package io.openaev.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManagerException;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantRegistrationExecutor unit tests")
class TenantRegistrationExecutorTest {

  @Mock private EntityManager entityManager;
  @Mock private Session session;
  @Mock private Filter filter;
  @Mock private BuiltinTenantRegistrable registrable1;
  @Mock private BuiltinTenantRegistrable registrable2;

  private TenantRegistrationExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new TenantRegistrationExecutor(List.of(registrable1, registrable2), entityManager);
  }

  private Tenant createTenant(String id, String name) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.setName(name);
    return tenant;
  }

  private void stubSessionMocks() {
    when(entityManager.unwrap(Session.class)).thenReturn(session);
    when(session.enableFilter("tenantFilter")).thenReturn(filter);
    when(filter.setParameter(eq("tenantId"), any())).thenReturn(filter);
  }

  @Nested
  @DisplayName("Isolated registration (startup path — REQUIRES_NEW)")
  class IsolatedRegistration {

    @BeforeEach
    void setUp() {
      stubSessionMocks();
    }

    @Test
    @DisplayName("given_validTenant_should_registerAllBuiltins")
    void given_validTenant_should_registerAllBuiltins() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");

      // Act
      executor.registerForTenantIsolated(tenant);

      // Assert
      verify(registrable1).registerForTenant();
      verify(registrable2).registerForTenant();
    }

    @Test
    @DisplayName("given_validTenant_should_setTenantContextAndFilter")
    void given_validTenant_should_setTenantContextAndFilter() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");

      // Act
      executor.registerForTenantIsolated(tenant);

      // Assert — enableFilter called twice: once to set tenant-1, once to restore previous
      verify(session, times(2)).enableFilter("tenantFilter");
      verify(filter).setParameter("tenantId", "tenant-1");
    }

    @Test
    @DisplayName("given_validTenant_should_syncRlsSessionVariable")
    void given_validTenant_should_syncRlsSessionVariable() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");

      // Act
      executor.registerForTenantIsolated(tenant);

      // Assert — doWork called twice: once to set RLS for tenant-1, once to restore previous
      verify(session, times(2)).doWork(any(Work.class));
    }

    @Test
    @DisplayName("given_validTenant_should_restorePreviousTenantContext")
    void given_validTenant_should_restorePreviousTenantContext() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");
      TenantContext.setCurrentTenant("previous-tenant");

      // Act
      executor.registerForTenantIsolated(tenant);

      // Assert
      assertThat(TenantContext.getCurrentTenant()).isEqualTo("previous-tenant");
    }

    @Test
    @DisplayName("given_failingRegistrable_should_restoreTenantContext")
    void given_failingRegistrable_should_restoreTenantContext() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");
      TenantContext.setCurrentTenant("original");
      doThrow(new RuntimeException("boom")).when(registrable1).registerForTenant();

      // Act
      try {
        executor.registerForTenantIsolated(tenant);
      } catch (DependenciesManagerException ignored) {
      }

      // Assert
      assertThat(TenantContext.getCurrentTenant()).isEqualTo("original");
    }
  }

  @Nested
  @DisplayName("Join-transaction registration (tenant creation path)")
  class JoinTransactionRegistration {

    @Test
    @DisplayName("given_validTenant_should_registerAllBuiltins")
    void given_validTenant_should_registerAllBuiltins() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");

      // Act
      executor.registerForTenant(tenant);

      // Assert
      verify(registrable1).registerForTenant();
      verify(registrable2).registerForTenant();
    }

    @Test
    @DisplayName("given_validTenant_should_notSetFilterOrRls")
    void given_validTenant_should_notSetFilterOrRls() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");

      // Act
      executor.registerForTenant(tenant);

      // Assert — caller is responsible for tenant context setup
      verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("given_failingRegistrable_should_throwDependenciesManagerException")
    void given_failingRegistrable_should_throwDependenciesManagerException() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");
      doThrow(new RuntimeException("boom")).when(registrable1).registerForTenant();

      // Act & Assert
      assertThatThrownBy(() -> executor.registerForTenant(tenant))
          .isInstanceOf(DependenciesManagerException.class)
          .hasMessageContaining("Tenant One");
    }

    @Test
    @DisplayName("given_failingRegistrable_should_notCallSubsequentRegistrables")
    void given_failingRegistrable_should_notCallSubsequentRegistrables() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-1", "Tenant One");
      doThrow(new RuntimeException("boom")).when(registrable1).registerForTenant();

      // Act
      try {
        executor.registerForTenant(tenant);
      } catch (DependenciesManagerException ignored) {
      }

      // Assert
      verify(registrable2, never()).registerForTenant();
    }
  }
}
