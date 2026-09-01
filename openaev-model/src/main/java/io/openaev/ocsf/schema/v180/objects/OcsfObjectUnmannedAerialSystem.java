package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectUnmannedAerialSystem extends OcsfObject {
  /** The endpoint hardware information. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hw_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDeviceHwInfo hwInfoField;

  /** The detailed geographical location usually associated with an IP address. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation locationField;

  /** The model name of the aircraft or unmanned system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT modelField;

  /** The name of the unmanned system as reported by tracking or sensing hardware. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The serial number of the unmanned system. This is expressed in <code>CTA-2063-A</code> format.
   */
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

  /** The type of the UAS. For example, Helicopter, Gyroplane, Rocket, etc. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The UAS type identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * A secondary identification identifier for an unmanned system. This can be a Serial Number (in
   * <code>CTA-2063-A</code> format, the Registration ID (provided by the <code>CAA</code>, a UTM,
   * or a unique Session ID.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /**
   * The primary identification identifier for an unmanned system. This can be a Serial Number (in
   * <code>CTA-2063-A</code> format, the Registration ID (provided by the <code>CAA</code>, a UTM,
   * or a unique Session ID.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The Unmanned Aircraft System Traffic Management (UTM) provided universal unique ID (UUID)
   * traceable to a non-obfuscated ID where this UTM UUID acts as a 'session id' to protect exposure
   * of operationally sensitive information.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uuid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUuidT uuidField;

  /**
   * Vertical speed upward relative to the WGS-84 datum, measured in meters per second. Special
   * Values: <code>Invalid</code>, <code>No Value</code>, or <code>Unknown: 63 m/s</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vertical_speed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT verticalSpeedField;
}
