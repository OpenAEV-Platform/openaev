package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectResourceDetails extends OcsfObject {
  /** A list of <code>agent</code> objects associated with a device, endpoint, or resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAgent> agentListField;

  /**
   * The logical grouping or isolated segment within a cloud provider's infrastructure where the
   * resource is located. Examples include AWS partitions (aws, aws-cn, aws-us-gov), Azure cloud
   * environments (AzureCloud, AzureUSGovernment, AzureChinaCloud), or similar logical divisions in
   * other cloud providers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud_partition")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cloudPartitionField;

  /** The time when the resource was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the resource was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /**
   * Criticality or relative importance of this resource, normalized to the caption of <code>
   * criticality_id</code>. In the case of Other, the value is defined by the event source.
   *
   * <p>Note: For versions prior to 1.9, <code>criticality_id</code> was not available and this is a
   * source specific value.
   *
   * <p>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "criticality")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT criticalityField;

  /**
   * The normalized identifier for the criticality or relative importance of this resource. Select
   * the value that best reflects the operational and security impact if the resource were
   * compromised or made unavailable. See the <code>criticality</code> sibling attribute for the
   * corresponding human-readable label.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "criticality_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT criticalityIdField;

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

  /** Additional data describing the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v190.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  /**
   * The device details when the resource type is a device — for example, a cloud compute instance,
   * a laptop, or a network appliance. Provides structured device attributes (OS, type, hardware,
   * agent) that the resource's scalar fields cannot represent — beyond what the <code>hostname
   * </code> and <code>ip</code> attributes capture. The <code>device.name</code> and <code>
   * device.uid</code> should match the <code>name</code> and <code>uid</code> of this objects; the
   * <code>device.hostname</code> value should match the <code>hostname</code> attribute of this
   * object. The top-level <code>hostname</code> and <code>ip</code> remain the primary identifiers
   * for the device resource.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice deviceField;

  /** The name of the related resource group. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup groupField;

  /**
   * The fully qualified name of the resource. If the resource is a device, this should match <code>
   * device.hostname</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  /**
   * The IP address of the resource, in either IPv4 or IPv6 format. If the resource is a device,
   * this should match <code>device.ip</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIpT ipField;

  /**
   * Indicates whether the device or resource has a backup enabled, such as an automated snapshot or
   * a cloud backup. For example, this is indicated by the <code>cloudBackupEnabled</code> value
   * within JAMF Pro mobile devices or the registration of an AWS ARN with the AWS Backup service.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_backed_up")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isBackedUpField;

  /** The list of labels associated to the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> labelsField;

  /** The time when the resource was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the resource was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The name of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The namespace is useful when similar entities exist that you need to keep separate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT namespaceField;

  /**
   * The details of the entity that owns the resource. Usually not the same as <code>user</code>
   * that may not own the resource. For example the owner of the directory server but not a user in
   * the directory. This object includes properties such as the owner's name, unique identifier,
   * type, domain, and other relevant attributes that help identify the resource owner within the
   * environment.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser ownerField;

  /**
   * The cloud service provider that hosts or manages the resource. This field is typically used
   * when the resource is managed by a different provider than the one generating the event or
   * finding. Examples include AWS, Azure, GCP (Google Cloud Platform), Oracle Cloud, IBM Cloud,
   * Alibaba Cloud, or other public, private, or hybrid cloud providers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT providerField;

  /**
   * The cloud region where the resource is hosted, as defined by the cloud provider. This
   * represents the physical or logical geographic area containing the infrastructure supporting the
   * resource. Examples include AWS regions (us-east-1, eu-west-1), Azure regions (East US, West
   * Europe), GCP regions (us-central1, europe-west1), or Oracle Cloud regions (us-ashburn-1,
   * uk-london-1).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT regionField;

  /**
   * A graph representation showing how this resource relates to and interacts with other entities
   * in the environment. This can include parent/child relationships, dependencies, or other
   * connections.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_relationship")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGraph resourceRelationshipField;

  /**
   * The role of the resource in the context of the event or finding, normalized to the caption of
   * the role_id value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "role")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT roleField;

  /** The normalized identifier of the resource's role in the context of the event or finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "role_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT roleIdField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  /** The resource type as defined by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The alternative unique identifier of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier of the resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /**
   * The user represented by this resource — for example, a cloud IAM user, a local account, or an
   * application identity. Provides structured user attributes (email, groups, privileges) that this
   * object's scalar fields such as <code>name</code> cannot represent. Distinct from <code>owner
   * </code>, which identifies the entity responsible for managing the resource. The <code>user.name
   * </code> value should match the <code>name</code> attribute of this object, <code>user.uid
   * </code> should match the <code>uid</code> of this object.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;

  /** The version of the resource. For example <code>1.2.3</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  /**
   * The availability zone within a cloud region where the resource is located. Examples include AWS
   * availability zones (us-east-1a, us-east-1b), Azure availability zones (1, 2, 3 within a
   * region), GCP zones (us-central1-a, us-central1-b), or Oracle Cloud availability domains (AD-1,
   * AD-2, AD-3).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT zoneField;
}
