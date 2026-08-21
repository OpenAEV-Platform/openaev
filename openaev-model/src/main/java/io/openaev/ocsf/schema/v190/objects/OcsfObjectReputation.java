package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectReputation extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "score_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT scoreIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "base_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT baseScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT providerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT scoreField;
}
