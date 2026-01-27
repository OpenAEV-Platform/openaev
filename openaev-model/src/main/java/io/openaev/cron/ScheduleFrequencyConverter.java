package io.openaev.cron;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for {@link ScheduleFrequency} enum values.
 *
 * <p>This converter handles the serialization and deserialization of {@link ScheduleFrequency}
 * values between their Java enum representation and the string format stored in the database.
 *
 * <p>Example usage in an entity:
 *
 * <pre>{@code
 * @Convert(converter = ScheduleFrequencyConverter.class)
 * @Column(name = "schedule_frequency")
 * private ScheduleFrequency frequency;
 * }</pre>
 *
 * @see ScheduleFrequency
 */
@Converter
public class ScheduleFrequencyConverter implements AttributeConverter<ScheduleFrequency, String> {

  /**
   * Converts a {@link ScheduleFrequency} enum value to its database string representation.
   *
   * @param attribute the enum value to convert
   * @return the short code string (e.g., "d" for DAILY)
   */
  @Override
  public String convertToDatabaseColumn(ScheduleFrequency attribute) {
    return attribute.toString();
  }

  /**
   * Converts a database string value to its corresponding {@link ScheduleFrequency} enum.
   *
   * @param dbData the database string to convert
   * @return the corresponding {@link ScheduleFrequency} enum value
   * @throws IllegalArgumentException if the string does not match any known frequency
   */
  @Override
  public ScheduleFrequency convertToEntityAttribute(String dbData) {
    return ScheduleFrequency.fromString(dbData);
  }
}
