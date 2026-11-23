package io.openaev.scheduler;

import static io.openaev.scheduler.jobs.AssetFreshnessJob.ASSET_FRESHNESS_JOB;
import static io.openaev.scheduler.jobs.AssetFreshnessJob.ASSET_FRESHNESS_TRIGGER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("PlatformTriggers: the asset freshness trigger")
class PlatformTriggersAssetFreshnessTest {

  @Test
  @DisplayName("given an interval should fire the freshness job at that interval, forever")
  void given_interval_should_fireTheFreshnessJobAtThatInterval() {
    // Arrange
    PlatformTriggers triggers = new PlatformTriggers();
    triggers.setPlatformJobs(new PlatformJobDefinitions());
    ReflectionTestUtils.setField(triggers, "assetFreshnessIntervalMinutes", 15);

    // Act
    Trigger trigger = triggers.assetFreshnessTrigger();

    // Assert
    assertThat(trigger.getKey().getName()).isEqualTo(ASSET_FRESHNESS_TRIGGER);
    assertThat(trigger.getJobKey().getName()).isEqualTo(ASSET_FRESHNESS_JOB);
    assertThat(trigger).isInstanceOf(SimpleTrigger.class);
    SimpleTrigger simple = (SimpleTrigger) trigger;
    assertThat(simple.getRepeatInterval()).isEqualTo(15L * 60L * 1000L);
    assertThat(simple.getRepeatCount()).isEqualTo(SimpleTrigger.REPEAT_INDEFINITELY);
  }
}
