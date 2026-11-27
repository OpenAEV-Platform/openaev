package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The call site the fail-closed detector reports is the code that asked for the read, not a frame
 * that only forwards it.
 */
@DisplayName("Fail-closed detector caller frame")
class FailClosedDetectorCallerFrameTest {

  @Test
  @DisplayName("given the lazy-collection tracker, should treat it as a forwarding frame")
  void given_lazyCollectionTracker_should_beAForwardingFrame() {
    // Arrange
    String tracker = LazyCollectionInitTracker.class.getName() + "$Tracking";

    // Act
    boolean forwarding = FailClosedDetectorListener.isForwardingFrame(tracker);

    // Assert
    assertThat(forwarding).isTrue();
  }

  @Test
  @DisplayName("given a service that reads, should not treat it as a forwarding frame")
  void given_aServiceThatReads_should_notBeAForwardingFrame() {
    // Arrange
    String service = "io.openaev.service.UserService";

    // Act
    boolean forwarding = FailClosedDetectorListener.isForwardingFrame(service);

    // Assert
    assertThat(forwarding).isFalse();
  }
}
