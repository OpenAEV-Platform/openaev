package io.openaev.service.catalog.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.service.catalog.Installed;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.openaev.service.catalog.scenario.CatalogScenarioInstaller;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Catalogue auto-install listener")
class CatalogAutoInstallListenerTest {

  private static final String TENANT_ID = "tenant";

  @Mock private CatalogPayloadInstaller catalogPayloadInstaller;
  @Mock private CatalogScenarioInstaller catalogScenarioInstaller;
  @Mock private EntityManager entityManager;

  @Test
  @DisplayName(
      "Installs payloads then scenarios in the tenant of the release, then gives the thread its"
          + " tenant back")
  void given_releaseApplied_should_installPayloadsThenScenariosInItsTenant() {
    // Arrange
    TenantContext.setCurrentTenant("previous");
    AtomicReference<String> tenantDuringInstall = new AtomicReference<>();
    TxCtx ctx = TxCtx.forTenant(TENANT_ID);
    when(catalogPayloadInstaller.autoInstall(ctx, TENANT_ID))
        .thenReturn(new Installed<>(List.of(), List.of()));
    when(catalogScenarioInstaller.autoInstall(ctx, TENANT_ID))
        .thenAnswer(
            invocation -> {
              tenantDuringInstall.set(TenantContext.getCurrentTenant());
              return new Installed<>(List.of(), List.of());
            });

    CatalogAutoInstallListener listener =
        new CatalogAutoInstallListener(
            catalogPayloadInstaller,
            catalogScenarioInstaller,
            new AmbientTenantBridge(entityManager));

    // Act
    listener.onReleaseApplied(new CatalogReleaseApplied(TENANT_ID, "1.261110.0", List.of()));

    // Assert
    InOrder order = inOrder(catalogPayloadInstaller, catalogScenarioInstaller);
    order.verify(catalogPayloadInstaller).autoInstall(ctx, TENANT_ID);
    order.verify(catalogScenarioInstaller).autoInstall(ctx, TENANT_ID);
    assertThat(tenantDuringInstall).hasValue(TENANT_ID);
    assertThat(TenantContext.getCurrentTenant()).isEqualTo("previous");
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }
}
