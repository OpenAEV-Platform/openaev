package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCloud extends OcsfObject {
  /**
   * The Account object containing details about the cloud account, subscription, or billing unit
   * where the event or finding was created. This object includes properties such as the account
   * name, unique identifier, type, labels, and tags.<br>
   * <br>
   * <strong>Examples:</strong>
   *
   * <ul>
   *   <li><strong>AWS:</strong> Account object with <code>name</code>, <code>uid</code> (Account
   *       ID), <code>type</code>, and other account properties
   *   <li><strong>Azure:</strong> Subscription object with <code>name</code>, <code>uid</code>
   *       (Subscription ID), <code>type</code>, and subscription metadata
   *   <li><strong>GCP:</strong> Project object with <code>name</code>, <code>uid</code> (Project
   *       ID), <code>type</code>, and project attributes
   *   <li><strong>Oracle Cloud:</strong> Compartment object with <code>name</code>, <code>uid
   *       </code> (Tenancy OCID), <code>type</code>, and compartment details
   * </ul>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "account")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAccount accountField;

  /**
   * The logical grouping or isolated segment within a cloud provider's infrastructure where the
   * event or finding was created, often used for compliance, governance, or regional separation.
   * <br>
   * <br>
   * <strong>Examples:</strong>
   *
   * <ul>
   *   <li><strong>AWS:</strong> Partition where the event occurred (<code>aws</code>, <code>aws-cn
   *       </code>, <code>aws-us-gov</code>)
   *   <li><strong>Azure:</strong> Cloud environment where the event occurred (<code>AzureCloud
   *       </code>, <code>AzureUSGovernment</code>, <code>AzureChinaCloud</code>)
   * </ul>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud_partition")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cloudPartitionField;

  /**
   * The Organization object containing details about the organizational unit or management
   * structure that governs the account, subscription, or project where the event or finding was
   * created. This object includes properties such as the organization name, unique identifier,
   * type, and other organizational metadata.<br>
   * <br>
   * <strong>Examples:</strong>
   *
   * <ul>
   *   <li><strong>AWS:</strong> Organization object with <code>name</code>, <code>uid</code>
   *       (Organization ID), <code>type</code>, and other organizational properties
   *   <li><strong>Azure:</strong> Management Group object with <code>name</code>, <code>uid</code>
   *       (Management Group ID), <code>type</code>, and management group metadata
   *   <li><strong>GCP:</strong> Organization object with <code>name</code>, <code>uid</code>
   *       (Organization ID), <code>type</code>, and organizational attributes
   *   <li><strong>Oracle Cloud:</strong> Tenancy object with <code>name</code>, <code>uid</code>
   *       (Tenancy OCID), <code>type</code>, and tenancy details
   * </ul>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "org")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOrganization orgField;

  /** The unique identifier of a Cloud project. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "project_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT projectUidField;

  /**
   * The unique name of the Cloud services provider where the event or finding was created. Examples
   * include AWS, Azure, GCP (Google Cloud Platform), Oracle Cloud, IBM Cloud, Alibaba Cloud, or
   * other public, private, or hybrid cloud providers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT providerField;

  /**
   * The cloud region where the event or finding was created, as defined by the cloud provider.<br>
   * <br>
   * <strong>Examples:</strong>
   *
   * <ul>
   *   <li><strong>AWS:</strong> Region where the event occurred (<code>us-east-1</code>, <code>
   *       eu-west-1</code>)
   *   <li><strong>Azure:</strong> Region where the event occurred (<code>East US</code>, <code>
   *       West Europe</code>)
   *   <li><strong>GCP:</strong> Region where the event occurred (<code>us-central1</code>, <code>
   *       europe-west1</code>)
   *   <li><strong>Oracle Cloud:</strong> Region where the event occurred (<code>us-ashburn-1</code>
   *       , <code>uk-london-1</code>)
   * </ul>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT regionField;

  /**
   * The availability zone in the cloud region where the event or finding was created, as defined by
   * the cloud provider.<br>
   * <br>
   * <strong>Examples:</strong>
   *
   * <ul>
   *   <li><strong>AWS:</strong> Availability zone where the event occurred (<code>us-east-1a</code>
   *       , <code>us-east-1b</code>)
   *   <li><strong>Azure:</strong> Availability zone where the event occurred (<code>1</code>,
   *       <code>2</code>, <code>3</code> within a region)
   *   <li><strong>GCP:</strong> Availability zone where the event occurred (<code>us-central1-a
   *       </code>, <code>us-central1-b</code>)
   *   <li><strong>Oracle Cloud:</strong> Availability zone where the event occurred (<code>AD-1
   *       </code>, <code>AD-2</code>, <code>AD-3</code>)
   * </ul>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT zoneField;
}
