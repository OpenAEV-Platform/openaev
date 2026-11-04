package io.openaev.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CatalogItem;
import io.openaev.notification.content.ContentNotificationEvents.CatalogReleaseApplied;
import io.openaev.notification.content.ContentNotificationEvents.ScenariosInstalledAutomatically;
import io.openaev.rest.exception.BadRequestException;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("Automatic install of the new scenarios of a release")
class CatalogScenarioAutoInstallerTest {

  private static final String TENANT = "tenant-a";

  private CatalogInstallService catalogInstallService;
  private ApplicationEventPublisher publisher;
  private CatalogScenarioAutoInstaller installer;

  @BeforeEach
  void setUp() {
    catalogInstallService = mock(CatalogInstallService.class);
    publisher = mock(ApplicationEventPublisher.class);
    TenantScopedTransaction tenantTx = mock(TenantScopedTransaction.class);
    when(tenantTx.executeNew(any(TxCtx.class), ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get());
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantTx)
        .executeNew(any(TxCtx.class), ArgumentMatchers.<Runnable>any());
    installer = new CatalogScenarioAutoInstaller(catalogInstallService, tenantTx, publisher);
  }

  private static CatalogItem scenario(String externalId, String name) {
    CatalogItem item = new CatalogItem();
    item.setExternalId(externalId);
    item.setName(name);
    item.setVersion("1.0.0");
    return item;
  }

  private void plan(CatalogItem... items) {
    when(catalogInstallService.isAutoInstall(TENANT)).thenReturn(true);
    when(catalogInstallService.installAllPlan())
        .thenReturn(new CatalogInstallService.InstallAllPlan(List.of(items), List.of()));
  }

  @Test
  @DisplayName("A scenario that fails does not stop the others")
  void given_oneScenarioFails_should_installTheOthers() throws Exception {
    // Arrange
    plan(scenario("easm", "EASM Scenario"), scenario("confluence", "Confluence"));
    when(catalogInstallService.install(any(), eq(TENANT), eq("easm"), anyString(), isNull()))
        .thenThrow(new BadRequestException("This scenario runs a revoked payload"));

    // Act
    installer.onReleaseApplied(
        new CatalogReleaseApplied(TENANT, "2026.10.30", false, List.of(), List.of()));

    // Assert
    verify(catalogInstallService)
        .install(any(), eq(TENANT), eq("confluence"), anyString(), isNull());
    ArgumentCaptor<ScenariosInstalledAutomatically> event =
        ArgumentCaptor.forClass(ScenariosInstalledAutomatically.class);
    verify(publisher).publishEvent(event.capture());
    assertThat(event.getValue().installed()).containsExactly("Confluence");
    assertThat(event.getValue().failed()).containsExactly("EASM Scenario");
  }

  @Test
  @DisplayName("Nothing is installed while the switch is off")
  void given_switchOff_should_installNothing() throws Exception {
    // Arrange
    when(catalogInstallService.isAutoInstall(TENANT)).thenReturn(false);

    // Act
    installer.onReleaseApplied(
        new CatalogReleaseApplied(TENANT, "2026.10.30", false, List.of(), List.of()));

    // Assert
    verify(catalogInstallService, never()).install(any(), any(), any(), any(), any());
    verify(publisher, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("The first release installs without a notification")
  void given_firstSync_should_installSilently() throws Exception {
    // Arrange
    plan(scenario("easm", "EASM Scenario"));

    // Act
    installer.onReleaseApplied(
        new CatalogReleaseApplied(TENANT, "2026.10.30", true, List.of(), List.of()));

    // Assert
    verify(catalogInstallService).install(any(), eq(TENANT), eq("easm"), anyString(), isNull());
    verify(publisher, never()).publishEvent(any(Object.class));
  }
}
