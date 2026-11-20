package io.openaev.notification.catalog;

import static io.openaev.database.model.NotificationTriggerEventType.INSTALLED_AUTOMATICALLY;
import static io.openaev.database.model.NotificationTriggerEventType.RELEASE_RECEIVED;
import static io.openaev.database.model.NotificationTriggerEventType.SYNC_BLOCKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.notification.catalog.CatalogEvents.AddedVersion;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseRefused;
import io.openaev.notification.catalog.CatalogEvents.InstalledAutomatically;
import io.openaev.notification.catalog.CatalogEvents.RefusedVersion;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.service.catalog.CatalogOffer;
import io.openaev.service.catalog.scenario.CatalogScenarioService;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Catalogue release notifications")
class CatalogNotificationListenerTest {

  private static final String TENANT = "tenant-a";
  private static final String RELEASE = "1.261030.0";
  private static final String REPOSITORY = "filigran";

  private NotificationEngineService engine;
  private CatalogScenarioService catalogScenarioService;
  private CatalogNotificationListener listener;

  @BeforeEach
  void setUp() {
    engine = mock(NotificationEngineService.class);
    catalogScenarioService = mock(CatalogScenarioService.class);
    TenantScopedTransaction tenantTx = mock(TenantScopedTransaction.class);
    when(tenantTx.executeReadOnly(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    listener =
        new CatalogNotificationListener(
            new ContentNotifier(engine, tenantTx), catalogScenarioService);
  }

  private String sentMessage(String release, NotificationTriggerEventType eventType) {
    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Supplier<StreamVisibility>> visibility = ArgumentCaptor.forClass(Supplier.class);
    verify(engine)
        .handleEventWithMessage(
            eq(NotificationResourceCatalog.CATALOG_SYNC),
            eq(release),
            eq(TENANT),
            eq(eventType),
            message.capture(),
            visibility.capture());
    assertThat(visibility.getValue().get()).isEqualTo(StreamVisibility.UNFILTERED);
    return message.getValue();
  }

  private void verifyNothingSent() {
    verify(engine, never())
        .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
  }

  private static AddedVersion added(
      ContentType type, String externalId, String name, String version, boolean isNew) {
    return new AddedVersion(type, REPOSITORY, externalId, name, version, isNew);
  }

  private static CatalogReleaseApplied applied(boolean firstSync, List<AddedVersion> added) {
    return new CatalogReleaseApplied(TENANT, RELEASE, firstSync, added, List.of());
  }

  @Nested
  @DisplayName("Release received")
  class ReleaseReceived {

    @Test
    @DisplayName("The first release fills the catalogue: it tells nothing")
    void given_firstSync_should_sendNothing() {
      // Act
      listener.onReleaseApplied(
          applied(true, List.of(added(ContentType.SCENARIO, "easm", "EASM", "1.0.0", true))));

      // Assert
      verifyNothingSent();
    }

    @Test
    @DisplayName("A release without news tells nothing")
    void given_nothingAdded_should_sendNothing() {
      // Act
      listener.onReleaseApplied(applied(false, List.of()));

      // Assert
      verifyNothingSent();
    }

    @Test
    @DisplayName("New scenarios, updates of installed ones and new actions are told in one message")
    void given_newContent_should_tellWhatTheReleaseBrings() {
      // Arrange
      when(catalogScenarioService.installedScenarioIds(eq(TENANT), any()))
          .thenReturn(Map.of(CatalogOffer.key(REPOSITORY, "ransomware"), "scenario-id"));

      // Act
      listener.onReleaseApplied(
          applied(
              false,
              List.of(
                  added(ContentType.SCENARIO, "easm", "EASM", "1.0.0", true),
                  added(ContentType.SCENARIO, "ransomware", "Ransomware", "1.1.0", false),
                  added(ContentType.SCENARIO, "ransomware", "Ransomware", "1.2.0", false),
                  added(ContentType.PAYLOAD, "nmap", "Nmap", "1.0.0", true),
                  added(ContentType.PAYLOAD, "nmap", "Nmap", "1.1.0", false),
                  added(ContentType.PAYLOAD, "curl", "Curl", "2.0.0", false))));

      // Assert
      assertThat(sentMessage(RELEASE, RELEASE_RECEIVED))
          .isEqualTo(
              "[catalog] Release 1.261030.0: 1 new scenario (EASM);"
                  + " 1 installed scenario with an update available (Ransomware v1.2.0);"
                  + " 1 new action");
    }

    @Test
    @DisplayName("What the release installed by itself, and what failed, is told at once")
    void given_installedAutomatically_should_tellItOnce() {
      // Act
      listener.onInstalledAutomatically(
          new InstalledAutomatically(
              TENANT,
              RELEASE,
              List.of("payload-1", "payload-2"),
              List.of("EASM", "Ransomware"),
              List.of("phishing v1.0.0")));

      // Assert
      assertThat(sentMessage(RELEASE, INSTALLED_AUTOMATICALLY))
          .isEqualTo(
              "[catalog] Release 1.261030.0: 2 actions installed automatically;"
                  + " 2 scenarios installed automatically (EASM, Ransomware);"
                  + " 1 item failed to install (phishing v1.0.0)");
    }
  }

  @Nested
  @DisplayName("Sync blocked")
  class SyncBlocked {

    @Test
    @DisplayName("A refused item sends a sync blocked event")
    void given_refusedItem_should_sendSyncBlocked() {
      // Act
      listener.onReleaseApplied(
          new CatalogReleaseApplied(
              TENANT,
              RELEASE,
              true,
              List.of(),
              List.of(new RefusedVersion("EASM Scenario", "1.0.0", "Archive hash mismatch"))));

      // Assert
      assertThat(sentMessage(RELEASE, SYNC_BLOCKED))
          .isEqualTo(
              "[catalog] Release 1.261030.0: 1 item refused"
                  + "\n- EASM Scenario v1.0.0: Archive hash mismatch");
    }

    @Test
    @DisplayName("A release refused as a whole sends a sync blocked event with the reason")
    void given_refusedRelease_should_sendSyncBlocked() {
      // Act
      listener.onReleaseRefused(
          new CatalogReleaseRefused(
              TENANT, "1.261105.0", "Unsupported manifest schema version: 2"));

      // Assert
      assertThat(sentMessage("1.261105.0", SYNC_BLOCKED))
          .isEqualTo(
              "[catalog] Release 1.261105.0 refused: Unsupported manifest schema version: 2");
    }

    @Test
    @DisplayName("An unreadable manifest is reported without a release name")
    void given_unreadableManifest_should_sendWithoutName() {
      // Act
      listener.onReleaseRefused(new CatalogReleaseRefused(TENANT, null, "Unreadable manifest"));

      // Assert
      assertThat(sentMessage("without a name", SYNC_BLOCKED))
          .isEqualTo("[catalog] Release without a name refused: Unreadable manifest");
    }
  }
}
