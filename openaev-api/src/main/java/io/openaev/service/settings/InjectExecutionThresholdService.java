package io.openaev.service.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The inject execution threshold: the number of minutes OpenAEV waits for an inject sent to its
 * agents (or to an agentless injector) to report a terminal trace before the finalization job
 * records a timeout and ends it.
 *
 * <p>A {@link RuntimeSetting}: the {@code INJECT_EXECUTION_THRESHOLD_MINUTES} environment variable
 * wins and locks it; else the value an administrator set in Settings &gt; Parameters; else the
 * {@code inject.execution.threshold.minutes} property. Every read goes to the database: a change is
 * applied at the next run of the finalization job, without a restart.
 */
@Service
@RequiredArgsConstructor
public class InjectExecutionThresholdService {

  public static final int MIN_THRESHOLD_MINUTES =
      RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES.min();
  public static final int MAX_THRESHOLD_MINUTES =
      RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES.max();

  private final RuntimeSettingsService runtimeSettingsService;

  /** The threshold in force. */
  public int thresholdMinutes() {
    return runtimeSettingsService.intValue(RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES);
  }
}
