package io.openaev.service;

import io.openaev.database.model.Inject;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import lombok.Data;

@Data
public class InjectTime {
  private boolean isRelativeDay = false;
  private boolean isRelativeHour = false;
  private boolean isRelativeMinute = false;
  private boolean specifyDays = true;
  private DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
  private Instant date;
  private String unformattedDate;
  private int relativeDayNumber;
  private int relativeHourNumber;
  private int relativeMinuteNumber;
  private Inject linkedInject;
}
