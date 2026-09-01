package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectProduct extends OcsfObject {
  /**
   * The Common Platform Enumeration (CPE) name as described by (<a target='_blank'
   * href='https://nvd.nist.gov/products/cpe'>NIST</a>) For example: <code>cpe:/a:apple:safari:16.2
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpe_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cpeNameField;

  /**
   * The Data Classification object includes information about data classification levels and data
   * category types.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification dataClassificationField;

  /**
   * A list of Data Classification objects, that include information about data classification
   * levels and data category types, identified by a classifier.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  /** The feature that reported the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "feature")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFeature featureField;

  /**
   * The two letter lower case language codes, as defined by <a target='_blank'
   * href='https://en.wikipedia.org/wiki/ISO_639-1'>ISO 639-1</a>. For example: <code>en</code>
   * (English), <code>de</code> (German), or <code>fr</code> (French).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "lang")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT langField;

  /** The name of the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The installation path of the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  /** The unique identifier of the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The URL pointing towards the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT urlStringField;

  /** The name of the vendor of the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vendorNameField;

  /**
   * The version of the product, as defined by the event source. For example: <code>2013.1.3-beta
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
