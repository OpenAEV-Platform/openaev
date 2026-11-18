package io.openaev.service.settings;

import static io.openaev.scheduler.jobs.InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES;

import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.repository.SettingRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The inject execution threshold: the number of minutes OpenAEV waits for an inject sent to its
 * agents (or to an agentless injector) to report a terminal trace before the finalization job
 * records a timeout and ends it.
 *
 * <p>It is a platform setting ({@link SettingKeys#INJECT_EXECUTION_THRESHOLD_MINUTES}, a {@code
 * tenant_id IS NULL} row) an administrator changes at runtime. While that row is absent, the value
 * is the {@code inject.execution.threshold.minutes} property ({@code
 * INJECT_EXECUTION_THRESHOLD_MINUTES}), so an instance that only sets the environment variable
 * behaves as before. Every read goes to the database: a change is applied at the next run of the
 * finalization job, without a restart.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InjectExecutionThresholdService {

  public static final int MIN_THRESHOLD_MINUTES = 1;
  public static final int MAX_THRESHOLD_MINUTES = 1440;

  private final SettingRepository settingRepository;

  @Value(
      "${inject.execution.threshold.minutes:" + DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES + "}")
  private Integer configuredThresholdMinutes;

  /** The threshold set by configuration, used while no administrator has set the setting. */
  public int configuredThresholdMinutes() {
    return configuredThresholdMinutes == null
        ? DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES
        : configuredThresholdMinutes;
  }

  /**
   * The threshold in force: the platform setting when it is set and valid, the configured value
   * otherwise.
   */
  public int thresholdMinutes() {
    return storedThresholdMinutes().orElseGet(this::configuredThresholdMinutes);
  }

  /** The platform setting, when an administrator set it to a valid value. */
  public Optional<Integer> storedThresholdMinutes() {
    String key = SettingKeys.INJECT_EXECUTION_THRESHOLD_MINUTES.key();
    return settingRepository
        .findByKeyAndTenantIsNull(key)
        .map(Setting::getValue)
        .filter(value -> value != null && !value.isBlank())
        .flatMap(
            value -> {
              try {
                int minutes = Integer.parseInt(value.trim());
                if (minutes < MIN_THRESHOLD_MINUTES || minutes > MAX_THRESHOLD_MINUTES) {
                  log.warn(
                      "Setting {} = {} is out of range [{}, {}], the configured value is used",
                      key,
                      value,
                      MIN_THRESHOLD_MINUTES,
                      MAX_THRESHOLD_MINUTES);
                  return Optional.empty();
                }
                return Optional.of(minutes);
              } catch (NumberFormatException e) {
                log.warn(
                    "Setting {} = {} is not an integer, the configured value is used", key, value);
                return Optional.empty();
              }
            });
  }
}
