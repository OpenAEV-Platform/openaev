package io.openaev.notification.catalog;

import static io.openaev.database.model.NotificationTriggerEventType.SYNC_BLOCKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseRefused;
import io.openaev.notification.catalog.CatalogEvents.RefusedVersion;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Catalogue sync blocked events")
class CatalogNotificationListenerTest {

  private static final String TENANT = "tenant-a";

  private NotificationEngineService engine;
  private CatalogNotificationListener listener;

  @BeforeEach
  void setUp() {
    engine = mock(NotificationEngineService.class);
    listener = new CatalogNotificationListener(engine);
  }

  private String sentMessage(String release) {
    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(engine)
        .handleEventWithMessage(
            eq(NotificationResourceCatalog.CATALOG_SYNC),
            eq(release),
            eq(TENANT),
            eq(SYNC_BLOCKED),
            message.capture());
    return message.getValue();
  }

  @Test
  @DisplayName("A refused item sends a sync blocked event")
  void given_refusedItem_should_sendSyncBlocked() {
    // Act
    listener.onReleaseApplied(
        new CatalogReleaseApplied(
            TENANT,
            "1.261030.0",
            List.of(new RefusedVersion("EASM Scenario", "1.0.0", "Archive hash mismatch"))));

    // Assert
    assertThat(sentMessage("1.261030.0"))
        .isEqualTo(
            "[catalog] Release 1.261030.0: 1 item refused"
                + "\n- EASM Scenario v1.0.0: Archive hash mismatch");
  }

  @Test
  @DisplayName("A release applied without refusal sends nothing")
  void given_nothingRefused_should_sendNothing() {
    // Act
    listener.onReleaseApplied(new CatalogReleaseApplied(TENANT, "1.261030.0", List.of()));

    // Assert
    verify(engine, never())
        .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
  }

  @Test
  @DisplayName("A release refused as a whole sends a sync blocked event with the reason")
  void given_refusedRelease_should_sendSyncBlocked() {
    // Act
    listener.onReleaseRefused(
        new CatalogReleaseRefused(TENANT, "1.261105.0", "Unsupported manifest schema version: 2"));

    // Assert
    assertThat(sentMessage("1.261105.0"))
        .isEqualTo("[catalog] Release 1.261105.0 refused: Unsupported manifest schema version: 2");
  }

  @Test
  @DisplayName("An unreadable manifest is reported without a release name")
  void given_unreadableManifest_should_sendWithoutName() {
    // Act
    listener.onReleaseRefused(new CatalogReleaseRefused(TENANT, null, "Unreadable manifest"));

    // Assert
    assertThat(sentMessage("without a name"))
        .isEqualTo("[catalog] Release without a name refused: Unreadable manifest");
  }
}
