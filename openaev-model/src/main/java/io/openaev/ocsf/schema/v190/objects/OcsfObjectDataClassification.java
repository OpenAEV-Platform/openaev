package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDataClassification extends OcsfObject {
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

  /** Details about the data discovered by classification job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "discovery_details")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDiscoveryDetails>
      discoveryDetailsField;

  /**
   * Details about the data policy that governs data handling and security measures related to
   * classification.
   */
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
