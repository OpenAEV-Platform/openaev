package io.openaev.notification.payload;

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
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.notification.catalog.CatalogEvents.InstalledAutomatically;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.catalog.event.ContentChanged;
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
  private PayloadService payloadService;
  private Payload older;
  private Payload installed;
  private PayloadInstallNotificationListener listener;

  @BeforeEach
  void setUp() {
    engine = mock(NotificationEngineService.class);
    payloadVersionService = mock(PayloadVersionService.class);
    payloadService = mock(PayloadService.class);
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
    when(payloadService.versionsOf(TENANT, installed)).thenReturn(List.of(older, installed));
    when(payloadService.payload(TENANT, "payload-2")).thenReturn(installed);
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

  private static ContentChanged changed(
      ContentType type, ContentChange.Action action, ContentChange.Mode mode) {
    return new ContentChanged(
        TENANT, type, REPOSITORY, EXTERNAL_ID, null, "1.1.0", action, mode, null);
  }

  private void runInSaltTyphoon() {
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
  }

  private void verifyScenarioTold() {
    verify(engine)
        .handleEventWithMessage(
            NotificationResourceCatalog.SCENARIO,
            SCENARIO_ID,
            TENANT,
            NEW_PAYLOAD_VERSION,
            "[scenario] Salt Typhoon: Network discovery v1.1.0 is available,"
                + " 1 inject still runs an older version");
    verifyNoMoreInteractions(engine);
  }

  @Test
  @DisplayName("A version installed by hand tells the scenarios running an older one")
  void given_manualInstalledLatest_should_tellScenarios() {
    // Arrange
    runInSaltTyphoon();

    // Act
    listener.onContentChanged(
        changed(ContentType.PAYLOAD, ContentChange.Action.INSTALL, ContentChange.Mode.MANUAL));

    // Assert
    verifyScenarioTold();
  }

  @Test
  @DisplayName("Versions a release installed by themselves tell the scenarios at once")
  void given_autoInstalledLatest_should_tellScenarios() {
    // Arrange
    runInSaltTyphoon();

    // Act
    listener.onInstalledAutomatically(
        new InstalledAutomatically(
            TENANT, "1.261030.0", List.of("payload-2"), List.of(), List.of()));

    // Assert
    verifyScenarioTold();
  }

  @Test
  @DisplayName("A version installed by hand, run by no scenario yet, tells nothing")
  void given_manualInstallUnused_should_tellNothing() {
    // Arrange
    when(payloadVersionService.scenarioUsages(TENANT, List.of(older))).thenReturn(Map.of());

    // Act
    listener.onContentChanged(
        changed(ContentType.PAYLOAD, ContentChange.Action.INSTALL, ContentChange.Mode.MANUAL));

    // Assert
    verifyNoMoreInteractions(engine);
  }

  @Test
  @DisplayName(
      "A scenario install, an uninstall or a version installed by itself is not told one by one")
  void given_otherChange_should_readNothing() {
    // Act
    listener.onContentChanged(
        changed(ContentType.SCENARIO, ContentChange.Action.INSTALL, ContentChange.Mode.AUTO));
    listener.onContentChanged(
        changed(ContentType.PAYLOAD, ContentChange.Action.UNINSTALL, ContentChange.Mode.MANUAL));
    listener.onContentChanged(
        changed(ContentType.PAYLOAD, ContentChange.Action.INSTALL, ContentChange.Mode.AUTO));

    // Assert
    verifyNoInteractions(engine, payloadVersionService);
  }
}
