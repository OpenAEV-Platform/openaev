package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Notification message rendered per recipient")
class NotificationMessageTest {

  private static final NotificationMessage.Reference ASSET =
      new NotificationMessage.Reference(
          NotificationResourceCatalog.ASSET, "asset-id", "db-prod-01");
  private static final NotificationMessage.Reference FINDING =
      new NotificationMessage.Reference(NotificationResourceCatalog.FINDING, "finding-id", "CVE-1");

  @Test
  @DisplayName("A message without references renders as itself")
  void plainMessage() {
    assertEquals(
        "[scenario] S updated", NotificationMessage.of("[scenario] S updated").render(r -> false));
  }

  @Test
  @DisplayName("Each hidden related object is named Restricted, the readable ones keep their name")
  void hiddenReferencesAreRestricted() {
    NotificationMessage message =
        new NotificationMessage("{0} has finding {1}", List.of(ASSET, FINDING));

    assertEquals("db-prod-01 has finding CVE-1", message.render(r -> true));
    assertEquals("Restricted has finding Restricted", message.render(r -> false));
    assertEquals("db-prod-01 has finding Restricted", message.render(r -> r.equals(ASSET)));
  }

  @Test
  @DisplayName("A failing check, an unknown index or a missing name fail closed")
  void failsClosed() {
    NotificationMessage throwing = new NotificationMessage("{0}", List.of(ASSET));
    assertEquals(
        "Restricted",
        throwing.render(
            r -> {
              throw new IllegalStateException("boom");
            }));
    assertEquals("Restricted", new NotificationMessage("{3}", List.of(ASSET)).render(r -> true));
    NotificationMessage.Reference unnamed =
        new NotificationMessage.Reference(NotificationResourceCatalog.ASSET, "a", null);
    assertEquals("Restricted", new NotificationMessage("{0}", List.of(unnamed)).render(r -> true));
  }

  @Test
  @DisplayName("A name that looks like a placeholder is never expanded")
  void nameIsNotExpanded() {
    NotificationMessage.Reference tricky =
        new NotificationMessage.Reference(NotificationResourceCatalog.ASSET, "a", "{1} $1");
    NotificationMessage message = new NotificationMessage("{0} / {1}", List.of(tricky, FINDING));
    assertEquals("{1} $1 / Restricted", message.render(r -> r.equals(tricky)));
  }
}
