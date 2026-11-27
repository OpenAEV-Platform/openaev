package io.openaev.notification.payload;

import static io.openaev.database.model.NotificationTriggerEventType.PAYLOAD_REVOKED;
import static io.openaev.database.model.NotificationTriggerEventType.REVOCATION_LIFTED;
import static io.openaev.database.model.NotificationTriggerEventType.REVOKED;
import static io.openaev.utils.fixtures.RawScenarioUsageCountFixture.scenarioUsages;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.payload.PayloadRevocationChanged;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.fixtures.PayloadFixture;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Payload revocation notifications")
class PayloadRevocationNotificationListenerTest {

  private static final String TENANT = "tenant-a";
  private static final String PAYLOAD_ID = "payload-1";
  private static final String SCENARIO_ID = "scenario-1";

  private NotificationEngineService engine;
  private PayloadVersionService payloadVersionService;
  private Payload payload;
  private PayloadRevocationNotificationListener listener;

  @BeforeEach
  void setUp() {
    engine = mock(NotificationEngineService.class);
    payloadVersionService = mock(PayloadVersionService.class);
    PayloadService payloadService = mock(PayloadService.class);
    TenantScopedTransaction tenantTx = mock(TenantScopedTransaction.class);
    when(tenantTx.executeReadOnly(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    payload = PayloadFixture.createDefaultCommand();
    payload.setId(PAYLOAD_ID);
    payload.setName("Network discovery");
    payload.setVersion("1.2.0");
    when(payloadService.payload(TENANT, PAYLOAD_ID)).thenReturn(payload);
    listener =
        new PayloadRevocationNotificationListener(
            new ContentNotifier(engine, tenantTx), payloadService, payloadVersionService);
  }

  private void usedByScenario(int injects) {
    when(payloadVersionService.scenarioUsages(TENANT, List.of(payload)))
        .thenReturn(List.of(scenarioUsages(SCENARIO_ID, "Salt Typhoon", injects)));
  }

  @Nested
  @DisplayName("Revoked")
  class Revoked {

    @Test
    @DisplayName("A version revoked by Filigran tells its watchers and every scenario running it")
    void given_revokedByFiligran_should_tellPayloadAndScenarios() {
      // Arrange
      usedByScenario(2);

      // Act
      listener.onRevocationChanged(
          new PayloadRevocationChanged(TENANT, PAYLOAD_ID, true, "Revoked by Filigran", true));

      // Assert
      verify(engine)
          .handleEventWithMessage(
              NotificationResourceCatalog.PAYLOAD,
              PAYLOAD_ID,
              TENANT,
              REVOKED,
              "[payload] Network discovery v1.2.0 revoked by Filigran");
      verify(engine)
          .handleEventWithMessage(
              NotificationResourceCatalog.SCENARIO,
              SCENARIO_ID,
              TENANT,
              PAYLOAD_REVOKED,
              "[scenario] Salt Typhoon: Network discovery v1.2.0 revoked by Filigran,"
                  + " 2 injects can no longer run");
    }

    @Test
    @DisplayName("A version revoked by an admin, run by no scenario, tells only its watchers")
    void given_revokedByAdminAndUnused_should_tellOnlyPayload() {
      // Arrange
      when(payloadVersionService.scenarioUsages(TENANT, List.of(payload))).thenReturn(List.of());

      // Act
      listener.onRevocationChanged(
          new PayloadRevocationChanged(TENANT, PAYLOAD_ID, true, "Deletes user files", false));

      // Assert
      verify(engine)
          .handleEventWithMessage(
              NotificationResourceCatalog.PAYLOAD,
              PAYLOAD_ID,
              TENANT,
              REVOKED,
              "[payload] Network discovery v1.2.0 revoked: Deletes user files");
      verifyNoMoreInteractions(engine);
    }
  }

  @Test
  @DisplayName("A lifted revocation tells the version's watchers")
  void given_lifted_should_tellPayload() {
    // Act
    listener.onRevocationChanged(
        new PayloadRevocationChanged(TENANT, PAYLOAD_ID, false, "Deletes user files", false));

    // Assert
    verify(engine)
        .handleEventWithMessage(
            NotificationResourceCatalog.PAYLOAD,
            PAYLOAD_ID,
            TENANT,
            REVOCATION_LIFTED,
            "[payload] Network discovery v1.2.0: revocation lifted");
    verifyNoMoreInteractions(engine);
  }
}
