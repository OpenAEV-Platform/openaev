package io.openaev.executors.mde.client;

import static org.assertj.core.api.Assertions.assertThat;
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

  @Test
  @DisplayName("without a device group, filter restricts to onboarded devices seen recently")
  void given_noDeviceGroup_should_filterOnLastSeenAndOnboarded() {
    // Act
    String filter = MdeExecutorClient.buildDevicesFilter(null, "2026-09-08T00:00:00Z");

    // Assert
    assertThat(filter)
        .isEqualTo("lastSeen gt 2026-09-08T00:00:00Z and onboardingStatus eq 'Onboarded'")
        .doesNotContain("rbacGroupId");
  }

  @Test
  @DisplayName("with a device group, filter scopes by rbacGroupId and stays onboarded-only")
  void given_deviceGroup_should_scopeByRbacGroupAndOnboarded() {
    // Act
    String filter = MdeExecutorClient.buildDevicesFilter("367", "2026-09-08T00:00:00Z");

    // Assert
    assertThat(filter)
        .isEqualTo(
            "rbacGroupId eq 367 and lastSeen gt 2026-09-08T00:00:00Z"
                + " and onboardingStatus eq 'Onboarded'");
  }

  @Test
  @DisplayName("a blank device group is treated as no group scoping")
  void given_blankDeviceGroup_should_notScopeByRbacGroup() {
    // Act
    String filter = MdeExecutorClient.buildDevicesFilter("   ", "2026-09-08T00:00:00Z");

    // Assert
    assertThat(filter)
        .isEqualTo("lastSeen gt 2026-09-08T00:00:00Z and onboardingStatus eq 'Onboarded'");
  }

  @Test
  @DisplayName("a device group id is trimmed before being placed in the filter")
  void given_paddedDeviceGroup_should_trimBeforeFiltering() {
    // Act
    String filter = MdeExecutorClient.buildDevicesFilter(" 367 ", "2026-09-08T00:00:00Z");

    // Assert
    assertThat(filter).startsWith("rbacGroupId eq 367 and ");
  }
}
