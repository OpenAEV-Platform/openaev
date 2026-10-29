package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The trigger install guard must not let one failed install disable the detector for every later
 * context of the fork. Under {@code maven.test.failure.ignore} the first context's failure is
 * swallowed by the build, so a guard marked before the install would leave the whole shard running
 * with no trigger and reporting nothing.
 */
@DisplayName("Write-attribution trigger install guard")
class WriteAttrTriggerInstallGuardTest {

  @Nested
  @DisplayName("When the first install fails")
  class FirstInstallFails {

    @Test
    @DisplayName("Given a failing first install, should let the next context install")
    void given_failingFirstInstall_should_letTheNextContextInstall() {
      // Arrange
      WriteAttrTriggerInstallGuard guard = new WriteAttrTriggerInstallGuard();
      AtomicInteger secondInstalls = new AtomicInteger();

      // Act
      assertThrows(
          IllegalStateException.class,
          () ->
              guard.installOnce(
                  () -> {
                    throw new IllegalStateException("database unreachable");
                  }));
      guard.installOnce(secondInstalls::incrementAndGet);

      // Assert
      assertEquals(1, secondInstalls.get(), "the install after a failure must run, not be skipped");
      assertTrue(guard.isInstalled());
    }

    @Test
    @DisplayName("Given a failing install, should propagate the failure rather than swallow it")
    void given_failingInstall_should_propagateTheFailure() {
      // Arrange
      WriteAttrTriggerInstallGuard guard = new WriteAttrTriggerInstallGuard();

      // Act
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () ->
                  guard.installOnce(
                      () -> {
                        throw new java.sql.SQLException("permission denied");
                      }));

      // Assert
      assertTrue(thrown.getMessage().contains("cannot install the write-attribution trigger"));
      assertFalse(guard.isInstalled(), "a failed install must not mark the guard");
    }
  }

  @Nested
  @DisplayName("When the first install succeeds")
  class FirstInstallSucceeds {

    @Test
    @DisplayName("Given a successful install, should not install again")
    void given_successfulInstall_should_notInstallAgain() {
      // Arrange
      WriteAttrTriggerInstallGuard guard = new WriteAttrTriggerInstallGuard();
      AtomicInteger installs = new AtomicInteger();

      // Act
      guard.installOnce(installs::incrementAndGet);
      guard.installOnce(installs::incrementAndGet);

      // Assert
      assertEquals(1, installs.get(), "the trigger is installed once per fork");
    }
  }
}
