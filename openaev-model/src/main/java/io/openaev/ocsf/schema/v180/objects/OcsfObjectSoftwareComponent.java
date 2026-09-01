package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSoftwareComponent extends OcsfObject {
  /** The author(s) who published the software component. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "author")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT authorField;

  /** Cryptographic hash to identify the binary instance of a software component. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hash")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint hashField;

  /** The software license applied to this component. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "license")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT licenseField;

  /** The software component name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The Package URL (PURL) to identify the software component. This is a URL that uniquely
   * identifies the component, including the component's name, version, and type. The URL is used to
   * locate and retrieve the component's metadata and content.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "purl")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT purlField;

  /**
   * The package URL (PURL) of the component that this software component has a relationship with.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_component")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT relatedComponentField;

  /**
   * The relationship between two software components, normalized to the caption of the <code>
   * relationship_id</code> value. In the case of 'Other', it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "relationship")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT relationshipField;

  /** The normalized identifier of the relationship between two software components. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "relationship_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT relationshipIdField;

  /**
   * The type of software component, normalized to the caption of the <code>type_id</code> value. In
   * the case of 'Other', it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The type of software component. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The software component version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
