package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectApplication extends OcsfObject {
  /** The criticality of the application as defined by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "criticality")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT criticalityField;

  /** Additional data describing the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  /** A description or commentary for an application, usually retrieved from an upstream system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /** The name of the related application or associated resource group. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup groupField;

  /** The fully qualified name of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  /** The list of labels associated to the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> labelsField;

  /** The name of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The identity of the service or user account that owns the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  /** The cloud region of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT regionField;

  /**
   * A graph representation showing how this application relates to and interacts with other
   * entities in the environment. This can include parent/child relationships, dependencies, or
   * other connections.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_relationship")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGraph resourceRelationshipField;

  /** The risk level, normalized to the caption of the risk_level_id value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskLevelField;

  /** The normalized risk level id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  /** The risk score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskScoreField;

  /** The Software Bill of Materials (SBOM) associated with the application */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sbom")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSbom sbomField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject> tagsField;

  /**
   * The type of application as defined by the event source, e.g., <code>GitHub</code>, <code>
   * Azure Logic App</code>, or <code>Amazon Elastic BeanStalk</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /**
   * An alternative or contextual identifier for the application, such as a configuration,
   * organization, or license UID.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier for the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The URL of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl urlField;

  /** The semantic version of the application, e.g., <code>1.7.4</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
