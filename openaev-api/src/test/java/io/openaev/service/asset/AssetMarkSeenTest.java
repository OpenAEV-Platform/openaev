package io.openaev.service.asset;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Asset#markSeen: freshness stamp")
class AssetMarkSeenTest {

  private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

  @Test
  @DisplayName("Given an asset never seen should stamp the date and the source")
  void given_neverSeen_should_stampDateAndSource() {
    // Arrange
    Asset asset = new Endpoint();

    // Act
    boolean changed = asset.markSeen(T0, "agent:openaev_agent");

    // Assert
    assertThat(changed).isTrue();
    assertThat(asset.getLastSeen()).isEqualTo(T0);
    assertThat(asset.getLastSeenSource()).isEqualTo("agent:openaev_agent");
  }

  @Test
  @DisplayName("Given a heartbeat within the throttle from the same source should change nothing")
  void given_sameSourceWithinThrottle_should_changeNothing() {
    // Arrange
    Asset asset = new Endpoint();
    asset.markSeen(T0, "agent:openaev_agent");

    // Act
    boolean changed = asset.markSeen(T0.plus(Duration.ofMinutes(5)), "agent:openaev_agent");

    // Assert
    assertThat(changed).isFalse();
    assertThat(asset.getLastSeen()).isEqualTo(T0);
  }

  @Test
  @DisplayName("Given an observation past the throttle should move the stamp")
  void given_pastThrottle_should_moveTheStamp() {
    // Arrange
    Asset asset = new Endpoint();
    asset.markSeen(T0, "agent:openaev_agent");
    Instant later = T0.plus(Asset.SEEN_THROTTLE).plusSeconds(1);

    // Act
    boolean changed = asset.markSeen(later, "agent:openaev_agent");

    // Assert
    assertThat(changed).isTrue();
    assertThat(asset.getLastSeen()).isEqualTo(later);
  }

  @Test
  @DisplayName("Given an older observation should never move the stamp back")
  void given_olderObservation_should_notMoveBack() {
    // Arrange
    Asset asset = new Endpoint();
    asset.markSeen(T0, "agent:openaev_agent");

    // Act
    boolean changed = asset.markSeen(T0.minus(Duration.ofDays(2)), "agent:crowdstrike");

    // Assert
    assertThat(changed).isFalse();
    assertThat(asset.getLastSeen()).isEqualTo(T0);
    assertThat(asset.getLastSeenSource()).isEqualTo("agent:openaev_agent");
  }

  @Test
  @DisplayName("Given a new source within the throttle should move the stamp to it")
  void given_newSourceWithinThrottle_should_moveTheStamp() {
    // Arrange
    Asset asset = new Endpoint();
    asset.markSeen(T0, "agent:openaev_agent");
    Instant later = T0.plus(Duration.ofMinutes(10));

    // Act
    boolean changed = asset.markSeen(later, "agent:crowdstrike");

    // Assert
    assertThat(changed).isTrue();
    assertThat(asset.getLastSeen()).isEqualTo(later);
    assertThat(asset.getLastSeenSource()).isEqualTo("agent:crowdstrike");
  }

  @Test
  @DisplayName("Given no date should change nothing")
  void given_noDate_should_changeNothing() {
    // Act & Assert
    assertThat(new Endpoint().markSeen(null, "agent:openaev_agent")).isFalse();
  }
}
