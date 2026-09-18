package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectProduct extends OcsfObject {
  /**
   * The Common Platform Enumeration (CPE) name. For example: <code>cpe:/a:apple:safari:16.2</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpe_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpeNameField;

  /**
   * The Data Classification object includes information about data classification levels and data
   * category types.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification dataClassificationField;

  /**
   * A list of Data Classification objects, that include information about data classification
   * levels and data category types, identified by a classifier.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  /** The feature that reported the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "feature")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFeature featureField;

  /**
   * The two letter lower case language codes, as defined by ISO 639-1. For example: <code>en</code>
   * (English), <code>de</code> (German), or <code>fr</code> (French).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "lang")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT langField;

  /** The name of the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The installation path of the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  /** The unique identifier of the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /** The URL pointing towards the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT urlStringField;

  /** The name of the vendor of the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  /**
   * The version of the product, as defined by the event source. For example: <code>2013.1.3-beta
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
