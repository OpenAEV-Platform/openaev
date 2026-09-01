package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDataSecurity extends OcsfObject {
  /**
   * The name of the data classification category that data matched into, e.g. Financial, Personal,
   * Governmental, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryField;

  /** The normalized identifier of the data classification category. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT categoryIdField;

  /** Describes details about the classifier used for data classification. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "classifier_details")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectClassifierDetails classifierDetailsField;

  /**
   * The file content confidentiality, normalized to the confidentiality_id value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidentiality")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT confidentialityField;

  /** The normalized identifier of the file content confidentiality indicator. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidentiality_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidentialityIdField;

  /**
   * The name of the stage or state that the data was in. E.g., Data-at-Rest, Data-in-Transit, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_lifecycle_state")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dataLifecycleStateField;

  /**
   * The stage or state that the data was in when it was assessed or scanned by a data security
   * tool.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_lifecycle_state_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT dataLifecycleStateIdField;

  /** Specific pattern, algorithm, fingerprint, or model used for detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_pattern")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT detectionPatternField;

  /**
   * The data security tool or system that the finding, detection, or alert originated from,
   * normalized to the caption of <code>detection_system_id</code>. E.g., Endpoint, Secure Email
   * Gateway, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_system")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT detectionSystemField;

  /**
   * The normalized identifier of the data security tool or system type from which the finding,
   * detection, or alert originated. When the type is not listed, use <code>99</code> (Other) and
   * populate <code>detection_system</code> with the source-specific label.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_system_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT detectionSystemIdField;

  /** Details about the data discovered by classification job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "discovery_details")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDiscoveryDetails>
      discoveryDetailsField;

  /** A text, binary, file name, or datastore that matched against a detection rule. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pattern_match")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT patternMatchField;

  /** Details about the policy that triggered the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;

  /** Size of the data classified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT sizeField;

  /** The source URL pointing towards the full classification job details. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  /** The contextual description of the <code>status, status_id</code> value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_details")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      statusDetailsField;

  /**
   * The resultant status of the classification job normalized to the caption of the <code>status_id
   * </code> value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  /** The normalized status identifier of the classification job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT statusIdField;

  /** The total count of discovered entities, by the classification job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "total")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT totalField;

  /** The unique identifier of the classification job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
