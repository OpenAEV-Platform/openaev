package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAircraft extends OcsfObject {
  /** The detailed geographical location usually associated with an IP address. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation locationField;

  /** The model name of the aircraft or unmanned system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT modelField;

  /** The name of the aircraft, such as the such as the flight name or callsign. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The serial number of the aircraft. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "serial_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT serialNumberField;

  /** Provides quality/containment on horizontal ground speed. Measured in meters/second. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "speed_accuracy")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT speedAccuracyField;

  /**
   * Ground speed of flight. This value is provided in meters per second with a minimum resolution
   * of 0.25 m/s. Special Values: <code>Invalid</code>, <code>No Value</code>, or <code>
   * Unknown: 255 m/s</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "speed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT speedField;

  /**
   * Direction of flight expressed as a “True North-based” ground track angle. This value is
   * provided in clockwise degrees with a minimum resolution of 1 degree. If aircraft is not moving
   * horizontally, use the “Unknown” value
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "track_direction")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT trackDirectionField;

  /**
   * A secondary identification identifier for an aircraft, such as the 4-digit squawk (octal
   * representation).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /**
   * The primary identification identifier for an aircraft, such as the 24-bit International Civil
   * Aviation Organization (ICAO) identifier of the aircraft, as 6 hex digits.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /**
   * Vertical speed upward relative to the WGS-84 datum, measured in meters per second. Special
   * Values: <code>Invalid</code>, <code>No Value</code>, or <code>Unknown: 63 m/s</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vertical_speed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT verticalSpeedField;
}
