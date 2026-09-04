package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectReputation extends OcsfObject {
  /** The reputation score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "base_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT baseScoreField;

  /** The provider of the reputation information. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT providerField;

  /**
   * The reputation score, normalized to the caption of the score_id value. In the case of 'Other',
   * it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT scoreField;

  /** The normalized reputation score identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "score_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT scoreIdField;
}
