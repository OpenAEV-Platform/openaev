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
    ZonedDateTime zdt = seed.atZone(ZoneId.of("UTC"));
    int minute = zdt.getMinute();
    int hour = zdt.getHour();
    int dayOfMonth = zdt.getDayOfMonth();
    int dayOfWeek = zdt.getDayOfWeek().getValue();

    return switch (scheduling) {
      case ScheduleFrequency.DAILY -> // daily
          String.format("0 %d %d * * *", minute, hour);
      case ScheduleFrequency.WEEKLY -> // weekly
          String.format("0 %d %d * * %d", minute, hour, dayOfWeek);
      case ScheduleFrequency.MONTHLY -> // monthly
          String.format("0 %d %d %d * *", minute, hour, dayOfMonth);
      case ScheduleFrequency.ONESHOT -> // STIX is represented like X
          null;
    };
  }
}
