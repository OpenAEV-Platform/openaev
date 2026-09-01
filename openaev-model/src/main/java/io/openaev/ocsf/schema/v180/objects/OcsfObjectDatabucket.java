package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDatabucket extends OcsfObject {
  /** A list of <code>agent</code> objects associated with a device, endpoint, or resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAgent> agentListField;

  /**
   * The logical grouping or isolated segment within a cloud provider's infrastructure where the
   * databucket is located.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud_partition")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cloudPartitionField;

  /** The time when the databucket was known to have been created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the databucket was known to have been created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The criticality of the databucket as defined by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "criticality")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT criticalityField;

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

  /** Additional data describing the resource. */
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  /** The description of the databucket. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /**
   * The encryption details of the databucket. Should be populated if the databucket is encrypted.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "encryption_details")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectEncryptionDetails encryptionDetailsField;

  /** Details about the file/object within a databucket. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  /** The name of the related resource group. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup groupField;

  /** The group names to which the databucket belongs. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "groups")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup> groupsField;

  /** The fully qualified hostname of the databucket. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  /** The IP address of the resource, in either IPv4 or IPv6 format. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT ipField;

  /**
   * Indicates whether the device or resource has a backup enabled, such as an automated snapshot or
   * a cloud backup. For example, this is indicated by the <code>cloudBackupEnabled</code> value
   * within JAMF Pro mobile devices or the registration of an AWS ARN with the AWS Backup service.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_backed_up")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isBackedUpField;

  /** Indicates if the databucket is encrypted. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_encrypted")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isEncryptedField;

  /** Indicates if the databucket is publicly accessible. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_public")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isPublicField;

  /** The list of labels associated to the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> labelsField;

  /**
   * The most recent time when any changes, updates, or modifications were made within the
   * databucket.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /**
   * The most recent time when any changes, updates, or modifications were made within the
   * databucket.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The databucket name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The namespace is useful when similar entities exist that you need to keep separate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT namespaceField;

  /** The identity of the service or user account that owns the databucket. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  /** The cloud region of the databucket. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT regionField;

  /**
   * A graph representation showing how this databucket relates to and interacts with other entities
   * in the environment. This can include parent/child relationships, dependencies, or other
   * connections.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_relationship")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGraph resourceRelationshipField;

  /** The size of the databucket in bytes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject> tagsField;

  /** The databucket type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The normalized identifier of the databucket type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The alternative unique identifier of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier of the databucket. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The version of the resource. For example <code>1.2.3</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;

  /** The specific availability zone within a cloud region where the databucket is located. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT zoneField;
}
