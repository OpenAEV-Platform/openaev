package io.openaev.notification.content;

import static io.openaev.database.model.NotificationTriggerEventType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.api.threat_arsenal.PayloadUsageOutput;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CatalogItem;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadIdentity;
import io.openaev.database.model.User;
import io.openaev.notification.content.ContentNotificationEvents.*;
import io.openaev.notification.engine.NotificationContent;
import io.openaev.notification.engine.NotificationDispatchService;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.service.UserService;
import io.openaev.service.catalog.CatalogService;
import io.openaev.service.payload.PayloadVersionChangeService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.tenants.TenantService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

@DisplayName("Content notifications: one per event, with its message")
class ContentNotificationListenerTest {

  private static final String TENANT = "tenant-a";
  private static final String SCENARIO_ID = "scenario-1";

  private NotificationEngineService engine;
  private NotificationDispatchService dispatch;
  private TenantService tenantService;
  private UserService userService;
  private PayloadVersionService payloadVersionService;
  private PayloadVersionChangeService payloadVersionChangeService;
  private CatalogService catalogService;
  private ScenarioService scenarioService;
  private ContentNotificationListener listener;

  @BeforeEach
  void setUp() {
    engine = mock(NotificationEngineService.class);
    dispatch = mock(NotificationDispatchService.class);
    TenantScopedTransaction tenantTx = mock(TenantScopedTransaction.class);
    when(tenantTx.execute(any(TxCtx.class), ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get());
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), ArgumentMatchers.<Runnable>any());
    tenantService = mock(TenantService.class);
    userService = mock(UserService.class);
    payloadVersionService = mock(PayloadVersionService.class);
    payloadVersionChangeService = mock(PayloadVersionChangeService.class);
    catalogService = mock(CatalogService.class);
    scenarioService = mock(ScenarioService.class);
    listener =
        new ContentNotificationListener(
            engine,
            dispatch,
            tenantTx,
            tenantService,
            userService,
            payloadVersionService,
            payloadVersionChangeService,
            catalogService,
            scenarioService);
  }

  private static PayloadIdentity identity(boolean managed) {
    PayloadIdentity identity = new PayloadIdentity();
    identity.setManaged(managed);
    return identity;
  }

  private static Payload payload(String id, String version, PayloadIdentity identity) {
    Payload payload = new Payload();
    payload.setId(id);
    payload.setName("Network discovery");
    payload.setVersion(version);
    payload.setIdentity(identity);
    // Payload approval: a version is announced once approved.
    payload.setApprovalStatus(Payload.PAYLOAD_APPROVAL_STATUS.APPROVED);
    return payload;
  }

  private void usedByScenario(Payload payload, int injects) {
    when(payloadVersionChangeService.scenarioUsages(payload))
        .thenReturn(
            IntStream.range(0, injects)
                .mapToObj(
                    i ->
                        new PayloadUsageOutput(
                            PayloadUsageOutput.TYPE_INJECT,
                            "inject-" + i,
                            "Inject " + i,
                            SCENARIO_ID,
                            "Salt Typhoon"))
                .toList());
  }

  private void verifySent(
      NotificationResourceCatalog entry,
      String entityId,
      NotificationTriggerEventType type,
      String message) {
    verify(engine)
        .handleEventWithMessage(eq(entry), eq(entityId), eq(TENANT), eq(type), eq(message));
  }

  @Nested
  @DisplayName("Revocations")
  class Revocations {

    @Test
    @DisplayName("A revoked version tells its watchers and every scenario that runs it")
    void given_revokedVersion_should_sendRevokedAndPayloadRevoked() {
      // Arrange
      Payload payload = payload("payload-1", "1.2.0", identity(false));
      when(payloadVersionService.findById("payload-1")).thenReturn(payload);
      usedByScenario(payload, 2);

      // Act
      listener.onRevocationChanged(
          new PayloadRevocationChanged(TENANT, "payload-1", true, "dangerous command"));

      // Assert
      verifySent(
          NotificationResourceCatalog.PAYLOAD,
          "payload-1",
          REVOKED,
          "[payload] Network discovery v1.2.0 revoked: dangerous command");
      verifySent(
          NotificationResourceCatalog.SCENARIO,
          SCENARIO_ID,
          PAYLOAD_REVOKED,
          "[scenario] Salt Typhoon: Network discovery v1.2.0 revoked (dangerous command), 2"
              + " injects can no longer run");
    }

    @Test
    @DisplayName("A lifted revocation tells the payload's watchers only")
    void given_liftedRevocation_should_sendRevocationLifted() {
      // Arrange
      Payload payload = payload("payload-1", "1.2.0", identity(false));
      when(payloadVersionService.findById("payload-1")).thenReturn(payload);

      // Act
      listener.onRevocationChanged(new PayloadRevocationChanged(TENANT, "payload-1", false, null));

      // Assert
      verifySent(
          NotificationResourceCatalog.PAYLOAD,
          "payload-1",
          REVOCATION_LIFTED,
          "[payload] Network discovery v1.2.0: revocation lifted");
      verify(engine, never())
          .handleEventWithMessage(any(), anyString(), anyString(), eq(PAYLOAD_REVOKED), any());
    }

    @Test
    @DisplayName("A revocation list says Filigran revoked the version, in every tenant")
    void given_listRevocation_should_sendRevokedByFiligran() {
      // Arrange
      PayloadIdentity identity = identity(true);
      Payload payload = payload("payload-1", "1.2.0", identity);
      when(tenantService.findActiveTenantIds()).thenReturn(List.of(TENANT));
      when(payloadVersionService.findManagedIdentity("filigran", "network-discovery"))
          .thenReturn(Optional.of(identity));
      when(payloadVersionService.findVersions(identity)).thenReturn(List.of(payload));

      // Act
      listener.onListRevocationsChanged(
          new ListRevocationsChanged(
              "filigran",
              List.of(new ListedVersion("network-discovery", "1.2.0", "unsafe")),
              List.of()));

      // Assert
      verifySent(
          NotificationResourceCatalog.PAYLOAD,
          "payload-1",
          REVOKED,
          "[payload] Network discovery v1.2.0 revoked by Filigran: unsafe");
    }
  }

  @Nested
  @DisplayName("Versions")
  class Versions {

    @Test
    @DisplayName("A new latest version tells scenarios still on an older one")
    void given_newLatestVersion_should_sendNewPayloadVersion() {
      // Arrange
      PayloadIdentity identity = identity(false);
      identity.setLatestPayloadId("payload-2");
      Payload older = payload("payload-1", "1.0.0", identity);
      Payload added = payload("payload-2", "1.1.0", identity);
      when(payloadVersionService.findById("payload-2")).thenReturn(added);
      when(payloadVersionService.findVersions(identity)).thenReturn(List.of(older, added));
      usedByScenario(older, 1);

      // Act
      listener.onVersionAdded(new PayloadVersionAdded(TENANT, "payload-2"));

      // Assert
      verifySent(
          NotificationResourceCatalog.SCENARIO,
          SCENARIO_ID,
          NEW_PAYLOAD_VERSION,
          "[scenario] Salt Typhoon: Network discovery v1.1.0 is available, 1 inject still runs"
              + " an older version");
    }

    @Test
    @DisplayName("A latest version waiting for approval tells nobody until it is approved")
    void given_pendingLatestVersion_should_sendNothing() {
      // Arrange
      PayloadIdentity identity = identity(false);
      identity.setLatestPayloadId("payload-2");
      Payload added = payload("payload-2", "1.1.0", identity);
      added.setApprovalStatus(Payload.PAYLOAD_APPROVAL_STATUS.PENDING);
      when(payloadVersionService.findById("payload-2")).thenReturn(added);

      // Act
      listener.onVersionAdded(new PayloadVersionAdded(TENANT, "payload-2"));

      // Assert
      verify(engine, never())
          .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("A version that is not the latest tells nobody")
    void given_olderVersionAdded_should_sendNothing() {
      // Arrange
      PayloadIdentity identity = identity(false);
      identity.setLatestPayloadId("payload-3");
      when(payloadVersionService.findById("payload-2"))
          .thenReturn(payload("payload-2", "1.1.0", identity));

      // Act
      listener.onVersionAdded(new PayloadVersionAdded(TENANT, "payload-2"));

      // Assert
      verify(engine, never())
          .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("A new Filigran version tells the customized copies based on an older one")
    void given_newFiligranVersion_should_sendBaseUpdateAvailable() {
      // Arrange
      PayloadIdentity identity = identity(true);
      identity.setLatestPayloadId("payload-2");
      Payload older = payload("payload-1", "1.0.0", identity);
      Payload added = payload("payload-2", "1.1.0", identity);
      PayloadIdentity variant = identity(false);
      variant.setLatestPayloadId("copy-1");
      Payload copy = payload("copy-1", "1.0.1", variant);
      copy.setName("My discovery");
      copy.setOriginId("payload-1");
      when(payloadVersionService.findById("payload-2")).thenReturn(added);
      when(payloadVersionService.findById("copy-1")).thenReturn(copy);
      when(payloadVersionService.findVersions(identity)).thenReturn(List.of(older, added));
      when(payloadVersionService.findVariantsOf(identity)).thenReturn(List.of(variant));

      // Act
      listener.onVersionAdded(new PayloadVersionAdded(TENANT, "payload-2"));

      // Assert
      verifySent(
          NotificationResourceCatalog.PAYLOAD,
          "copy-1",
          BASE_UPDATE_AVAILABLE,
          "[payload] My discovery: Filigran v1.1.0 is available");
    }

    @Test
    @DisplayName("A version a release installs by itself tells the payload's watchers")
    void given_automaticInstall_should_sendInstalledAutomatically() {
      // Arrange
      when(payloadVersionService.findById("payload-1"))
          .thenReturn(payload("payload-1", "1.2.0", identity(true)));

      // Act
      listener.onInstalledAutomatically(
          new PayloadInstalledAutomatically(TENANT, "payload-1", "2026.10.30"));

      // Assert
      verifySent(
          NotificationResourceCatalog.PAYLOAD,
          "payload-1",
          INSTALLED_AUTOMATICALLY,
          "[payload] Network discovery v1.2.0 installed automatically from release 2026.10.30");
    }
  }

  @Nested
  @DisplayName("Releases")
  class Releases {

    private CatalogItem item(String id, ContentType type, String externalId, String name) {
      CatalogItem item = new CatalogItem();
      item.setId(id);
      item.setType(type);
      item.setRepository("filigran");
      item.setExternalId(externalId);
      item.setVersion("1.0.0");
      item.setName(name);
      when(catalogService.findVersions(type, "filigran", externalId)).thenReturn(List.of(item));
      return item;
    }

    @Test
    @DisplayName("A release sums up what is new for the tenant")
    void given_release_should_sendReleaseReceivedSummary() {
      // Arrange
      CatalogItem scenario = item("item-1", ContentType.SCENARIO, "easm", "EASM Scenario");
      CatalogItem action = item("item-2", ContentType.PAYLOAD, "discovery", "Discovery");
      when(catalogService.findAllById(List.of("item-1", "item-2")))
          .thenReturn(List.of(scenario, action));
      when(scenarioService.findInstalledCatalogVersions()).thenReturn(Map.of());

      // Act
      listener.onReleaseApplied(
          new CatalogReleaseApplied(
              TENANT, "2026.10.30", false, List.of("item-1", "item-2"), List.of()));

      // Assert
      verifySent(
          NotificationResourceCatalog.CATALOG,
          "2026.10.30",
          RELEASE_RECEIVED,
          "[catalog] Release 2026.10.30: 1 new scenario (EASM Scenario); 1 new action");
      verify(dispatch, never()).alert(anyString(), anyList(), any());
    }

    @Test
    @DisplayName("The first release fills the catalogue without a notification")
    void given_firstSync_should_sendNothing() {
      // Act
      listener.onReleaseApplied(
          new CatalogReleaseApplied(TENANT, "2026.10.30", true, List.of("item-1"), List.of()));

      // Assert
      verify(engine, never())
          .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("A refused item alerts the administrators, even on the first release")
    void given_refusedItem_should_alertAdministrators() {
      // Arrange
      User admin = new User();
      when(userService.findAdministrators(TENANT)).thenReturn(List.of(admin));

      // Act
      listener.onReleaseApplied(
          new CatalogReleaseApplied(
              TENANT,
              "2026.10.30",
              true,
              List.of(),
              List.of(new Refused("easm", "1.0.0", "Archive hash mismatch"))));

      // Assert
      ArgumentCaptor<NotificationContent.Group> group =
          ArgumentCaptor.forClass(NotificationContent.Group.class);
      verify(dispatch).alert(eq(TENANT), eq(List.of(admin)), group.capture());
      assertThat(group.getValue().events())
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.operation()).isEqualTo(SYNC_BLOCKED);
                assertThat(event.message())
                    .isEqualTo(
                        "[catalog] Release 2026.10.30: 1 item refused: easm v1.0.0 (Archive hash"
                            + " mismatch)");
              });
    }
  }
}
