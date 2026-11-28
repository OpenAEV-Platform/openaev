package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectApplication extends OcsfObject {
  /** The time when the application was known to have been created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the application was known to have been created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /**
   * Criticality or relative importance of a resource/object in question, normalized to the caption
   * of <code>criticality_id</code>. In the case of Other, the value is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "criticality")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT criticalityField;

  /** Criticality or relative importance of a resource/object in question. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "criticality_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT criticalityIdField;

  /** Additional data describing the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v1100.JsonNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeJsonT dataField;

  /** A description or commentary for an application, usually retrieved from an upstream system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT descField;

  /** The initial discovery time of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  /** The initial discovery time of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  /** The name of the related application or associated resource group. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectGroup groupField;

  /** The fully qualified name of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeHostnameT hostnameField;

  /** The list of labels associated to the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT> labelsField;

  /** The most recent discovery time of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  /** The most recent discovery time of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  /** The time when the application was last known to have been modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the application was last known to have been modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The name of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT nameField;

  /** The identity of the service or user account that owns the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectUser ownerField;

  /**
   * The software product that this application is an instance of, carrying product identity such as
   * <code>vendor_name</code>, <code>cpe_name</code>, and canonical <code>version</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectProduct productField;

  /** The cloud region of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT regionField;

  /**
   * A graph representation showing how this application relates to and interacts with other
   * entities in the environment. This can include parent/child relationships, dependencies, or
   * other connections.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_relationship")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectGraph resourceRelationshipField;

  /** The risk level, normalized to the caption of the risk_level_id value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT riskLevelField;

  /** The normalized risk level id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  /** The risk score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT riskScoreField;

  /** The Software Bill of Materials (SBOM) associated with the application */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sbom")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectSbom sbomField;

  /**
   * The URL to the application's record or detail page within the reporting event source (e.g., a
   * link to the application in the security tool's UI).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeUrlT srcUrlField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectKeyValueObject> tagsField;

  /**
   * The type of application as defined by the event source, e.g., <code>GitHub</code>, <code>
   * Azure Logic App</code>, or <code>Amazon Elastic BeanStalk</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT typeField;

  /**
   * An alternative or contextual identifier for the application, such as a configuration,
   * organization, or license UID.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier for the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;

  /** The URL of the application. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectUrl urlField;

  /** The semantic version of the application, e.g., <code>1.7.4</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT versionField;
}
