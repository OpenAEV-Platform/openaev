package io.openaev.service.catalog.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.notification.catalog.CatalogEvents.InstalledAutomatically;
import io.openaev.service.catalog.Installed;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.openaev.service.catalog.scenario.CatalogScenarioInstaller;
import io.openaev.service.catalog.scenario.InstallableScenarioVersion;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("Catalogue auto-install listener")
class CatalogAutoInstallListenerTest {

  private static final String TENANT_ID = "tenant";

  @Mock private CatalogPayloadInstaller catalogPayloadInstaller;
  @Mock private CatalogScenarioInstaller catalogScenarioInstaller;
  @Mock private EntityManager entityManager;
  @Mock private ApplicationEventPublisher eventPublisher;

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
            new AmbientTenantBridge(entityManager),
            eventPublisher);

    // Act
    listener.onReleaseApplied(
        new CatalogReleaseApplied(TENANT_ID, "1.261110.0", false, List.of(), List.of()));

    // Assert
    InOrder order = inOrder(catalogPayloadInstaller, catalogScenarioInstaller);
    order.verify(catalogPayloadInstaller).autoInstall(ctx, TENANT_ID);
    order.verify(catalogScenarioInstaller).autoInstall(ctx, TENANT_ID);
    assertThat(tenantDuringInstall).hasValue(TENANT_ID);
    assertThat(TenantContext.getCurrentTenant()).isEqualTo("previous");
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Tells in one event what the release installed by itself and what failed")
  void given_contentInstalled_should_publishItOnce() {
    // Arrange
    TxCtx ctx = TxCtx.forTenant(TENANT_ID);
    Payload payload = new Payload();
    payload.setId("payload-1");
    Scenario scenario = new Scenario();
    scenario.setName("EASM");
    InstallableScenarioVersion failed =
        new InstallableScenarioVersion(
            "filigran", "phishing", "1.0.0", null, null, null, List.of());
    when(catalogPayloadInstaller.autoInstall(ctx, TENANT_ID))
        .thenReturn(new Installed<>(List.of(payload), List.of()));
    when(catalogScenarioInstaller.autoInstall(ctx, TENANT_ID))
        .thenReturn(new Installed<>(List.of(scenario), List.of(failed)));
    CatalogAutoInstallListener listener =
        new CatalogAutoInstallListener(
            catalogPayloadInstaller,
            catalogScenarioInstaller,
            new AmbientTenantBridge(entityManager),
            eventPublisher);

    // Act
    listener.onReleaseApplied(
        new CatalogReleaseApplied(TENANT_ID, "1.261110.0", false, List.of(), List.of()));

    // Assert
    verify(eventPublisher)
        .publishEvent(
            new InstalledAutomatically(
                TENANT_ID,
                "1.261110.0",
                List.of("payload-1"),
                List.of("EASM"),
                List.of("phishing v1.0.0")));
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }
}
