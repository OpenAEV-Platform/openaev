package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectResourceDetails extends OcsfObject {
  /** A list of <code>agent</code> objects associated with a device, endpoint, or resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAgent> agentListField;

  /**
   * The logical grouping or isolated segment within a cloud provider's infrastructure where the
   * resource is located. Examples include AWS partitions (aws, aws-cn, aws-us-gov), Azure cloud
   * environments (AzureCloud, AzureUSGovernment, AzureChinaCloud), or similar logical divisions in
   * other cloud providers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud_partition")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cloudPartitionField;

  /** The time when the resource was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the resource was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The criticality of the resource as defined by the event source. */
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
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  /** The name of the related resource group. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup groupField;

  /** The fully qualified name of the resource. */
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

  /** The list of labels associated to the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> labelsField;

  /** The time when the resource was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the resource was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The name of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The namespace is useful when similar entities exist that you need to keep separate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT namespaceField;

  /**
   * The details of the entity that owns the resource. This object includes properties such as the
   * owner's name, unique identifier, type, domain, and other relevant attributes that help identify
   * the resource owner within the environment.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  /**
   * The cloud service provider that hosts or manages the resource. This field is typically used
   * when the resource is managed by a different provider than the one generating the event or
   * finding. Examples include AWS, Azure, GCP (Google Cloud Platform), Oracle Cloud, IBM Cloud,
   * Alibaba Cloud, or other public, private, or hybrid cloud providers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT providerField;

  /**
   * The cloud region where the resource is hosted, as defined by the cloud provider. This
   * represents the physical or logical geographic area containing the infrastructure supporting the
   * resource. Examples include AWS regions (us-east-1, eu-west-1), Azure regions (East US, West
   * Europe), GCP regions (us-central1, europe-west1), or Oracle Cloud regions (us-ashburn-1,
   * uk-london-1).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT regionField;

  /**
   * A graph representation showing how this resource relates to and interacts with other entities
   * in the environment. This can include parent/child relationships, dependencies, or other
   * connections.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_relationship")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGraph resourceRelationshipField;

  /**
   * The role of the resource in the context of the event or finding, normalized to the caption of
   * the role_id value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "role")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT roleField;

  /** The normalized identifier of the resource's role in the context of the event or finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "role_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT roleIdField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject> tagsField;

  /** The resource type as defined by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The alternative unique identifier of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The version of the resource. For example <code>1.2.3</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;

  /**
   * The availability zone within a cloud region where the resource is located. Examples include AWS
   * availability zones (us-east-1a, us-east-1b), Azure availability zones (1, 2, 3 within a
   * region), GCP zones (us-central1-a, us-central1-b), or Oracle Cloud availability domains (AD-1,
   * AD-2, AD-3).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT zoneField;
}
