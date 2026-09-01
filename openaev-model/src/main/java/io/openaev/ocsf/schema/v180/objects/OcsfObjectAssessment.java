package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAssessment extends OcsfObject {
  /**
   * The category that the assessment is part of. For example: <code>Prevention</code> or <code>
   * Windows 10</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT categoryField;

  /**
   * The description of the assessment criteria, or a description of the specific configuration or
   * signal the assessment is targeting.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /**
   * Determines whether the assessment against the specific configuration or signal meets the
   * assessments criteria. For example, if the assessment checks if a <code>Datastore</code> is
   * encrypted or not, having encryption would be evaluated as <code>true</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "meets_criteria")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT meetsCriteriaField;

  /**
   * The name of the configuration or signal being assessed. For example: <code>
   * Kernel Mode Code Integrity (KMCI)</code> or <code>publicAccessibilityState</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The details of any policy associated with an assessment. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectPolicy policyField;

  /**
   * The unique identifier of the configuration or signal being assessed. For example: the <code>
   * signal_id</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
