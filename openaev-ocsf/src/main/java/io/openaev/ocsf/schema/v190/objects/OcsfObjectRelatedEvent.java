package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRelatedEvent extends OcsfObject {
  /**
   * An array of MITRE ATT&CK® objects describing identified tactics, techniques & sub-techniques.
   * The objects are compatible with MITRE ATLAS™ tactics, techniques & sub-techniques.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack> attacksField;

  /**
   * The number of times that activity in the same logical group occurred, as reported by the
   * related Finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  /**
   * The time when the related event/finding was created. If the related event/finding is in OCSF
   * and is a Finding, then this value should be equal to <code>finding_info.created_time</code> in
   * the corresponding Finding. If the related event/finding is in OCSF and is not a Finding, then
   * this value should be equal to <code>time</code> in the corresponding event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /**
   * The time when the related event/finding was created. If the related event/finding is in OCSF
   * and is a Finding, then this value should be equal to <code>finding_info.created_time</code> in
   * the corresponding Finding. If the related event/finding is in OCSF and is not a Finding, then
   * this value should be equal to <code>time</code> in the corresponding event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** A description of the related event/finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  /**
   * The time when the finding was first observed. e.g. The time when a vulnerability was first
   * observed.<br>
   * It can differ from the <code>created_time</code> timestamp, which reflects the time this
   * finding was created.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  /**
   * The time when the finding was first observed. e.g. The time when a vulnerability was first
   * observed.<br>
   * It can differ from the <code>created_time</code> timestamp, which reflects the time this
   * finding was created.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  /**
   * The Kill Chain provides a detailed description of each phase and its associated activities
   * within the broader context of a cyber attack.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kill_chain")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKillChainPhase>
      killChainField;

  /**
   * The time when the finding was most recently observed. e.g. The time when a vulnerability was
   * most recently observed.<br>
   * It can differ from the <code>modified_time</code> timestamp, which reflects the time this
   * finding was last modified.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  /**
   * The time when the finding was most recently observed. e.g. The time when a vulnerability was
   * most recently observed.<br>
   * It can differ from the <code>modified_time</code> timestamp, which reflects the time this
   * finding was last modified.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  /** The time when the related event/finding was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the related event/finding was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /**
   * The observables array surfaces key indicators and entities from the event or finding in a
   * single, consistent location for downstream correlation and detection. Each entry references an
   * attribute path within the event (e.g., <code>src_endpoint.ip</code>) along with its type and
   * value, enabling consumers to extract IOCs without parsing the full event structure.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observables")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectObservable> observablesField;

  /** Details about the product that reported the related event/finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  /** The unique identifier of the product that reported the related event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT productUidField;

  /**
   * The event/finding severity label, normalized to the caption of the <code>severity_id</code>
   * value. When <code>severity_id</code> is <code>99</code> (Other), this attribute <b>must</b>
   * contain the source-specific severity label. For all other values, this should match the caption
   * defined for that <code>severity_id</code> enum value (e.g., <code>"High"</code> for <code>
   * severity_id: 4</code>).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT severityField;

  /**
   * The normalized identifier of the event/finding severity.The normalized severity is a
   * measurement the effort and expense required to manage and resolve an event or incident. Smaller
   * numerical values represent lower impact events, and larger numerical values represent higher
   * impact events.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT severityIdField;

  /**
   * The related event status. Should correspond to the label of the status_id (or 'Other' status
   * value for status_id = 99) of the related event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  /** The list of tags; <code>{key:value}</code> pairs associated with the related event/finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  /** A title or a brief phrase summarizing the related event/finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT titleField;

  /**
   * The list of key traits or characteristics extracted from the related event/finding that
   * influenced or contributed to the overall finding's outcome.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "traits")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectTrait> traitsField;

  /**
   * The type of the related event/finding.Populate if the related event/finding is <code>NOT</code>
   * in OCSF. If it is in OCSF, then utilize <code>type_name, type_uid</code> instead.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The type of the related OCSF event, as defined by <code>type_uid</code>.
   *
   * <p>For example: <code>Process Activity: Launch.</code>Populate if the related event/finding is
   * in OCSF.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeNameField;

  /**
   * The unique identifier of the related OCSF event type.
   *
   * <p>For example: <code>100701.</code>Populate if the related event/finding is in OCSF.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT typeUidField;

  /**
   * The unique identifier of the related event/finding. If the related event/finding is in OCSF,
   * then this value must be equal to <code>metadata.uid</code> in the corresponding event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
