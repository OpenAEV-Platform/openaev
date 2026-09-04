package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectUnmannedSystemOperatingArea extends OcsfObject {
  /**
   * Expressed as either height above takeoff location or height above ground level (AGL) for a UAS
   * current location. This value is provided in meters and must have a minimum resolution of 1 m.
   * Special Values: <code>Invalid</code>, <code>No Value</code>, or <code>Unknown: -1000 m</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "aerial_height")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT aerialHeightField;

  /**
   * Maximum altitude (WGS-84 HAE) for a group or an Intent-Based Network Participant. Measured in
   * meters. Special Values: <code>Invalid</code>, <code>No Value</code>, or <code>Unknown: -1000 m
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "altitude_ceiling")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT altitudeCeilingField;

  /**
   * Minimum altitude (WGS-84 HAE) for a group or an Intent-Based Network Participant. Measured in
   * meters. Special Values: <code>Invalid</code>, <code>No Value</code>, or <code>Unknown: -1000 m
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "altitude_floor")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT altitudeFloorField;

  /** The name of the city. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "city")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cityField;

  /** The name of the continent. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "continent")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT continentField;

  /**
   * A two-element array, containing a longitude/latitude pair. The format conforms with GeoJSON.
   * For example: <code>[-73.983, 40.719]</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "coordinates")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT> coordinatesField;

  /** Indicates the number of UAS in the operating area. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  /**
   * The ISO 3166-1 Alpha-2 country code.
   *
   * <p><b>Note:</b> The two letter country code should be capitalized. For example: <code>US</code>
   * or <code>CA</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "country")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT countryField;

  /** The description of the geographical location. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  /**
   * The date and time at which a group or an Intent-Based Network Participant operation ends. (This
   * field is only applicable to Network Remote ID.)
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /**
   * The date and time at which a group or an Intent-Based Network Participant operation ends. (This
   * field is only applicable to Network Remote ID.)
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT endTimeField;

  /**
   * The aircraft distance above or below the ellipsoid as measured along a line that passes through
   * the aircraft and is normal to the surface of the WGS-84 ellipsoid. This value is provided in
   * meters and must have a minimum resolution of 1 m. Special Values: <code>Invalid</code>, <code>
   * No Value</code>, or <code>Unknown: -1000 m</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "geodetic_altitude")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT geodeticAltitudeField;

  /**
   * Provides quality/containment on geodetic altitude. This is based on ADS-B Geodetic Vertical
   * Accuracy (GVA). Measured in meters.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "geodetic_vertical_accuracy")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT geodeticVerticalAccuracyField;

  /**
   * Geohash of the geo-coordinates (latitude and longitude).Geohashing is a geocoding system used
   * to encode geographic coordinates in decimal degrees, to a single string.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "geohash")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT geohashField;

  /**
   * Provides quality/containment on horizontal position. This is based on ADS-B NACp. Measured in
   * meters.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "horizontal_accuracy")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT horizontalAccuracyField;

  /** Indicates whether the location is on-premises. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_on_premises")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isOnPremisesField;

  /** The name of the Internet Service Provider (ISP). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ispField;

  /**
   * The geographical Latitude coordinate represented in Decimal Degrees (DD). For example: <code>
   * 42.361145</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "lat")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT latField;

  /**
   * A list of Position Location Information (PLI) (latitude/longitude pairs) defining the area
   * where a group or Intent-Based Network Participant operation is taking place. (This field is
   * only applicable to Network Remote ID.)
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "locations")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation> locationsField;

  /**
   * The geographical Longitude coordinate represented in Decimal Degrees (DD). For example: <code>
   * -71.057083</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "long")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT longField;

  /** The postal code of the location. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "postal_code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT postalCodeField;

  /**
   * The uncorrected barometric pressure altitude (based on reference standard 29.92 inHg, 1013.25
   * mb) provides a reference for algorithms that utilize 'altitude deltas' between aircraft. This
   * value is provided in meters and must have a minimum resolution of 1 m.. Special Values: <code>
   * Invalid</code>, <code>No Value</code>, or <code>Unknown: -1000 m</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pressure_altitude")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pressureAltitudeField;

  /** The provider of the geographical location data. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT providerField;

  /**
   * Farthest horizontal distance from the reported location at which any UA in a group may be
   * located (meters). Also allows defining the area where an Intent-Based Network Participant
   * operation is taking place. Default: 0 m.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "radius")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT radiusField;

  /**
   * The alphanumeric code that identifies the principal subdivision (e.g. province or state) of the
   * country. For example, 'CH-VD' for the Canton of Vaud, Switzerland
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT regionField;

  /**
   * The date and time at which a group or an Intent-Based Network Participant operation starts.
   * (This field is only applicable to Network Remote ID.)
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /**
   * The date and time at which a group or an Intent-Based Network Participant operation starts.
   * (This field is only applicable to Network Remote ID.)
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT startTimeField;

  /**
   * The type of operating area. For example, <code>Takeoff Location</code>, <code>Fixed Location
   * </code>, <code>Dynamic Location</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The operating area type identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;
}
