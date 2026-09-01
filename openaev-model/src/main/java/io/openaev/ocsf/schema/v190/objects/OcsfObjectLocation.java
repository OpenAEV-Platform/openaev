package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLocation extends OcsfObject {
  /**
   * Expressed as either height above takeoff location or height above ground level (AGL) for a UAS
   * current location. This value is provided in meters and must have a minimum resolution of 1 m.
   * Special Values: <code>Invalid</code>, <code>No Value</code>, or <code>Unknown: -1000 m</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "aerial_height")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT aerialHeightField;

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
   * The alphanumeric code that identifies the principal subdivision (e.g. province or state) of the
   * country. For example, 'CH-VD' for the Canton of Vaud, Switzerland
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT regionField;
}
