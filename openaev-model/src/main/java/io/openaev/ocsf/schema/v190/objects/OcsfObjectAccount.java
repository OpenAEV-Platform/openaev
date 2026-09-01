package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAccount extends OcsfObject {
  /** Indicates if the account is disabled. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_disabled")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isDisabledField;

  /** Indicates if the account is locked. For example, due to the amount of failed logins. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_locked")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isLockedField;

  /**
   * Indicates whether synchronization with an on-premises directory service is enabled. For
   * example, Microsoft Entra Connect.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_on_premises_sync_enabled")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isOnPremisesSyncEnabledField;

  /** The list of labels associated to the account. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> labelsField;

  /**
   * The name of the account (e.g. <code> GCP Project name </code>, <code> Linux Account name
   * </code> or <code> AWS Account name</code>).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the account. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  /**
   * The account type, normalized to the caption of 'account_type_id'. In the case of 'Other', it is
   * defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The normalized account type identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The unique identifier of the account (e.g. <code> AWS Account ID </code>, <code> OCID </code>,
   * <code> GCP Project ID </code>, <code> Azure Subscription ID </code>, <code>
   *  Google Workspace Customer ID </code>, or <code> M365 Tenant UID</code>).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
