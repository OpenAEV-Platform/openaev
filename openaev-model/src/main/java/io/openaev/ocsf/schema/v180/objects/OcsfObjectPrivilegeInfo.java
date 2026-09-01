package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPrivilegeInfo extends OcsfObject {
  /** Indicates whether the privilege is unused within the analysis timeframe. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_unused")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isUnusedField;

  /** The most recent time this privilege was used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastUsedTimeDtField;

  /** The most recent time this privilege was used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastUsedTimeField;

  /**
   * The name of the privilege, action, or permission. Examples: <code>GetObject</code>, <code>
   * CreateStoreImageTask</code> (AWS); <code>Microsoft.Storage/storageAccounts/read</code> (Azure);
   * <code>storage.objects.get</code> (GCP).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The type or category of the privilege, normalized to the caption of the <code>type_id</code>
   * value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The normalized type of the privilege. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;
}
