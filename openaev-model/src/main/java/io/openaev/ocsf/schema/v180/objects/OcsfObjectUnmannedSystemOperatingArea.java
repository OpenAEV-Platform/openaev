package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectUnmannedSystemOperatingArea extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "aerial_height")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT aerialHeightField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "altitude_ceiling")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT altitudeCeilingField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "altitude_floor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT altitudeFloorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "city")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "continent")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT continentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "coordinates")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeFloatT> coordinatesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT countField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "country")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT countryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT endTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "geodetic_altitude")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT geodeticAltitudeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "geodetic_vertical_accuracy")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT geodeticVerticalAccuracyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "geohash")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT geohashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "horizontal_accuracy")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT horizontalAccuracyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_on_premises")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isOnPremisesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ispField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "lat")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeFloatT latField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "locations")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation> locationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "long")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeFloatT longField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "postal_code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT postalCodeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pressure_altitude")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pressureAltitudeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT providerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "radius")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT radiusField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT regionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT startTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;
}
