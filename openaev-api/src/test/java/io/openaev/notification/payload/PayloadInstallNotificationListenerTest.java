package io.openaev.notification.payload;

import static io.openaev.database.model.NotificationTriggerEventType.INSTALLED_AUTOMATICALLY;
import static io.openaev.database.model.NotificationTriggerEventType.NEW_PAYLOAD_VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.openaev.api.threat_arsenal.dto.PayloadUsageOutput;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.catalog.event.CatalogContentChanged;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.fixtures.PayloadFixture;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Catalogue payload install notifications")
class PayloadInstallNotificationListenerTest {

  private static final String TENANT = "tenant-a";
  private static final String REPOSITORY = "filigran";
  private static final String EXTERNAL_ID = "network-discovery";
  private static final String SCENARIO_ID = "scenario-1";

  private NotificationEngineService engine;
  private PayloadVersionService payloadVersionService;
  private Payload older;
  private Payload installed;
  private PayloadInstallNotificationListener listener;

  @BeforeEach
  void setUp() {
    engine = mock(NotificationEngineService.class);
    payloadVersionService = mock(PayloadVersionService.class);
    PayloadService payloadService = mock(PayloadService.class);
    TenantScopedTransaction tenantTx = mock(TenantScopedTransaction.class);
    when(tenantTx.executeReadOnly(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    PayloadIdentity identity = new PayloadIdentity();
    identity.setRepository(REPOSITORY);
    identity.setExternalId(EXTERNAL_ID);
    older = version(identity, "payload-1", "1.0.0");
    installed = version(identity, "payload-2", "1.1.0");
    identity.setLatestVersion(installed);
    when(payloadService.versions(TENANT, EXTERNAL_ID)).thenReturn(List.of(older, installed));
    listener =
        new PayloadInstallNotificationListener(
            new ContentNotifier(engine, tenantTx), payloadService, payloadVersionService);
  }

  private static Payload version(PayloadIdentity identity, String id, String version) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setId(id);
    payload.setName("Network discovery");
    payload.setVersion(version);
    payload.setIdentity(identity);
    return payload;
  }

  private static CatalogContentChanged changed(
      ContentType type, CatalogHistory.Action action, CatalogHistory.Mode mode) {
    return new CatalogContentChanged(
        TENANT, type, REPOSITORY, EXTERNAL_ID, "1.1.0", action, mode, null);
  }

  @Test
  @DisplayName(
      "A version installed by itself tells its watchers and the scenarios running an older one")
  void given_autoInstalledLatest_should_tellPayloadAndScenarios() {
    // Arrange
    when(payloadVersionService.scenarioUsages(TENANT, List.of(older)))
        .thenReturn(
            Map.of(
                SCENARIO_ID,
                List.of(
                    new PayloadUsageOutput(
                        PayloadUsageOutput.TYPE_INJECT,
                        "inject-1",
                        "Discover",
                        SCENARIO_ID,
                        "Salt Typhoon"))));

    // Act
    listener.onContentChanged(
        changed(ContentType.PAYLOAD, CatalogHistory.Action.INSTALL, CatalogHistory.Mode.AUTO));

    // Assert
    verify(engine)
        .handleEventWithMessage(
            NotificationResourceCatalog.PAYLOAD,
            "payload-2",
            TENANT,
            INSTALLED_AUTOMATICALLY,
            "[payload] Network discovery v1.1.0 installed automatically");
    verify(engine)
        .handleEventWithMessage(
            NotificationResourceCatalog.SCENARIO,
            SCENARIO_ID,
            TENANT,
            NEW_PAYLOAD_VERSION,
            "[scenario] Salt Typhoon: Network discovery v1.1.0 is available,"
                + " 1 inject still runs an older version");
  }

  @Test
  @DisplayName("A version installed by hand, run by no scenario yet, tells nothing")
  void given_manualInstallUnused_should_tellNothing() {
    // Arrange
    when(payloadVersionService.scenarioUsages(TENANT, List.of(older))).thenReturn(Map.of());

    // Act
    listener.onContentChanged(
        changed(ContentType.PAYLOAD, CatalogHistory.Action.INSTALL, CatalogHistory.Mode.MANUAL));

    // Assert
    verifyNoMoreInteractions(engine);
  }

  @Test
  @DisplayName("A scenario install or an uninstall is not a payload install")
  void given_otherChange_should_readNothing() {
    // Act
    listener.onContentChanged(
        changed(ContentType.SCENARIO, CatalogHistory.Action.INSTALL, CatalogHistory.Mode.AUTO));
    listener.onContentChanged(
        changed(ContentType.PAYLOAD, CatalogHistory.Action.UNINSTALL, CatalogHistory.Mode.MANUAL));

    // Assert
    verifyNoInteractions(engine, payloadVersionService);
  }
}
