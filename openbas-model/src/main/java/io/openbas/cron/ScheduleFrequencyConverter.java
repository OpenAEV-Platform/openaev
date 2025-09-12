package io.openbas.cron;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ScheduleFrequencyConverter implements AttributeConverter<ScheduleFrequency, String> {

  @Override
  public String convertToDatabaseColumn(ScheduleFrequency attribute) {
    return attribute.toString();
  }

  @Override
  public ScheduleFrequency convertToEntityAttribute(String dbData) {
    return ScheduleFrequency.fromString(dbData);
  }
}
