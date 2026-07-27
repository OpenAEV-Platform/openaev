package io.openaev.executors.mde.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.executors.mde.model.MdeMachineAction;
import io.openaev.utils.fixtures.MdeMachineActionFixture;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MdeExecutorClientTest {

  // isStalePendingAction is a private static helper. Invoke it via reflection to lock in the
  // stale-detection boundary logic without standing up the whole HTTP client / token auth.
  private static boolean isStalePendingAction(MdeMachineAction action, Instant staleBefore) {
    try {
      Method method =
          MdeExecutorClient.class.getDeclaredMethod(
              "isStalePendingAction", MdeMachineAction.class, Instant.class);
      method.setAccessible(true);
      return (boolean) method.invoke(null, action, staleBefore);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to invoke MdeExecutorClient#isStalePendingAction", e);
    }
  }

  @Test
  @DisplayName("given a Pending action older than the stale threshold, should be flagged stale")
  void given_pendingActionOlderThanThreshold_should_beStale() {
    // Arrange
    Instant staleBefore = Instant.now().minus(30, ChronoUnit.MINUTES);
    MdeMachineAction action =
        MdeMachineActionFixture.createPendingMachineAction(
            Instant.now().minus(2, ChronoUnit.HOURS));

    // Act & Assert
    assertTrue(isStalePendingAction(action, staleBefore));
  }

  @Test
  @DisplayName("given a fresh Pending action newer than the stale threshold, should not be stale")
  void given_freshPendingAction_should_notBeStale() {
    // Arrange
    Instant staleBefore = Instant.now().minus(30, ChronoUnit.MINUTES);
    MdeMachineAction action =
        MdeMachineActionFixture.createPendingMachineAction(
            Instant.now().minus(5, ChronoUnit.MINUTES));

    // Act & Assert
    assertFalse(isStalePendingAction(action, staleBefore));
  }

  @Test
  @DisplayName("given a Pending action with null creationDateTimeUtc, should not be stale")
  void given_nullCreationDateTime_should_notBeStale() {
    // Arrange
    Instant staleBefore = Instant.now().minus(30, ChronoUnit.MINUTES);
    MdeMachineAction action = MdeMachineActionFixture.createPendingMachineAction((String) null);

    // Act & Assert
    assertFalse(isStalePendingAction(action, staleBefore));
  }

  @Test
  @DisplayName(
      "given a Pending action with an unparseable creationDateTimeUtc, should not be stale")
  void given_unparseableCreationDateTime_should_notBeStale() {
    // Arrange
    Instant staleBefore = Instant.now().minus(30, ChronoUnit.MINUTES);
    MdeMachineAction action = MdeMachineActionFixture.createPendingMachineAction("not-a-timestamp");

    // Act & Assert
    assertFalse(isStalePendingAction(action, staleBefore));
  }
}
