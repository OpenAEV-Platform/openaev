package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectReputation extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "base_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeFloatT baseScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT providerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT scoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "score_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT scoreIdField;
}
