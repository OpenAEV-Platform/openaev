package io.openaev.service.cron;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.openaev.cron.ScheduleFrequency;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CronService {
  public Optional<Instant> getNextExecutionFromInstant(
      Instant reference, ZoneId tz, String cronExpression) {
    if (cronExpression == null || cronExpression.isBlank()) {
      return Optional.empty();
    }
    return ExecutionTime.forCron(
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING53))
                .parse(cronExpression))
        .nextExecution(reference.atZone(tz))
        .map(ChronoZonedDateTime::toInstant);
  }

  public String getCronExpression(ScheduleFrequency scheduling, Instant seed) {
    return getCronExpression(scheduling, null, seed);
  }

  private String getCronExpression(ScheduleFrequency scheduling, Integer digits, Instant seed) {
    ZonedDateTime zdt = seed.atZone(ZoneId.of("UTC"));
    int minute = zdt.getMinute();
    int hour = zdt.getHour();
    int dayOfMonth = zdt.getDayOfMonth();
    int dayOfWeek = zdt.getDayOfWeek().getValue();

    return switch (scheduling) {
      case HOURLY ->
          digits != null
              ? String.format("0 %d */%d * * *", minute, digits)
              : String.format("0 %d %d * * *", minute, hour);
      case ScheduleFrequency.DAILY -> // daily
          digits != null
              ? String.format("0 %d %d * * */%d", minute, hour, digits)
              : String.format("0 %d %d * * *", minute, hour);
      case ScheduleFrequency.WEEKLY -> // weekly
          digits != null
              ? String.format("0 %d %d * * %d/%d", minute, hour, dayOfWeek, digits * 7)
              : String.format("0 %d %d * * %d", minute, hour, dayOfWeek);
      case ScheduleFrequency.MONTHLY -> // monthly
          digits != null
              ? String.format("0 %d %d %d */%d *", minute, hour, dayOfMonth, digits)
              : String.format("0 %d %d %d * *", minute, hour, dayOfMonth);
      case ScheduleFrequency.ONESHOT -> // STIX is represented like X
          null;
    };
  }

  public String getCronExpression(String iso8601PeriodExpression, Instant seed) {
    String pattern = "PT?(?<digits>\\d+)(?<order>[HDWM])";
    Matcher matcher = Pattern.compile(pattern).matcher(iso8601PeriodExpression);
    if (matcher.find()) {
      int digits = Integer.parseInt(matcher.group("digits"));
      ScheduleFrequency freq = ScheduleFrequency.fromString(matcher.group("order"));
      return getCronExpression(freq, digits, seed);
    } else {
      throw new UnsupportedOperationException(
          "Expression %s does not conform to ISO 8601 period expression format"
              .formatted(iso8601PeriodExpression));
    }
  }
}
