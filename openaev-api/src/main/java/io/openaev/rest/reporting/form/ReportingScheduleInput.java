package io.openaev.rest.reporting.form;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingSchedulePeriod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportingScheduleInput {

  @JsonProperty("reporting_schedule_name")
  private String name;

  @JsonProperty("reporting_schedule_period")
  @NotNull(message = MANDATORY_MESSAGE)
  private ReportingSchedulePeriod period;

  /**
   * UTC firing time. Formats: DAY = {@code "HH:mm"}, WEEK = {@code "<1-7>-HH:mm"}, MONTH = {@code
   * "<1-31>-HH:mm"}; ignored for HOUR.
   */
  @JsonProperty("reporting_schedule_time")
  private String triggerTime;

  @JsonProperty("reporting_schedule_format")
  private ReportingFormat format;

  @JsonProperty("reporting_schedule_enabled")
  private Boolean enabled;

  @JsonProperty("reporting_schedule_recipient_users")
  private List<String> recipientUserIds;

  @JsonProperty("reporting_schedule_recipient_emails")
  private List<@Email(message = EMAIL_FORMAT) String> recipientEmails;
}
