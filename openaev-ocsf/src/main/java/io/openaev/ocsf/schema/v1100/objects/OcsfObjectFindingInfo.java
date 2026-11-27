package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFindingInfo extends OcsfObject {
  /**
   * The analytic technique used to analyze and derive insights from the data or information that
   * led to the finding or conclusion.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "analytic")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnalytic analyticField;

  /**
   * An Attack Graph describes possible routes an attacker could take through an environment. It
   * describes relationships between resources and their findings, such as malware detections,
   * vulnerabilities, misconfigurations, and other security actions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attack_graph")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectGraph attackGraphField;

  /**
   * An array of attack objects, each describing a tactic, technique, and/or sub-technique
   * associated with the finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectAttack> attacksField;

  /** The time when the finding was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the finding was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** A list of data sources utilized in generation of the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_sources")
  private java.util.List<io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT>
      dataSourcesField;

  /** The description of the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT descField;

  /**
   * The time when the finding was first observed. e.g. The time when a vulnerability was first
   * observed.
   *
   * <p>It can differ from the <code>created_time</code> timestamp, which reflects the time this
   * finding was created.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  /**
   * The time when the finding was first observed. e.g. The time when a vulnerability was first
   * observed.
   *
   * <p>It can differ from the <code>created_time</code> timestamp, which reflects the time this
   * finding was created.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  /**
   * The Kill Chain provides a detailed description of each phase and its associated activities
   * within the broader context of a cyber attack.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kill_chain")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectKillChainPhase>
      killChainField;

  /**
   * The time when the finding was most recently observed. e.g. The time when a vulnerability was
   * most recently observed.
   *
   * <p>It can differ from the <code>modified_time</code> timestamp, which reflects the time this
   * finding was last modified.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  /**
   * The time when the finding was most recently observed. e.g. The time when a vulnerability was
   * most recently observed.
   *
   * <p>It can differ from the <code>modified_time</code> timestamp, which reflects the time this
   * finding was last modified.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  /** The time when the finding was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the finding was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** Details about the product that reported the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectProduct productField;

  /** The unique identifier of the product that reported the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product_uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT productUidField;

  /**
   * Query information for related_events. Populate <code>query_info.query_string</code> with the
   * search criteria used to identify events, activities, or observables related to the finding.
   *
   * <p><b>Note:</b>The query does not need to be limited to events that directly contributed to the
   * finding's <code>analytic</code> result. It may also describe a broader relevant result set,
   * such as related observables, follow-up activity, or other events that can be investigated or
   * tracked after the finding is created.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_info")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectQueryInfo queryInfoField;

  /** Other analytics related to this finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_analytics")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnalytic>
      relatedAnalyticsField;

  /** Number of related events or findings. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_events_count")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT relatedEventsCountField;

  /**
   * Describes events and/or other findings related to the finding as identified by the security
   * product. Note that these events may or may not be in OCSF.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_events")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectRelatedEvent>
      relatedEventsField;

  /**
   * The time when the finding was resolved. Populate when the finding's <code>status_id</code> is 4
   * (Resolved).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resolved_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT resolvedTimeDtField;

  /**
   * The time when the finding was resolved. Populate when the finding's <code>status_id</code> is 4
   * (Resolved).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resolved_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT resolvedTimeField;

  /** The URL pointing to the source of the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeUrlT srcUrlField;

  /** The list of tags; <code>{key:value}</code> pairs associated with the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectKeyValueObject> tagsField;

  /** A title or a brief phrase summarizing the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT titleField;

  /** The list of key traits or characteristics extracted from the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "traits")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectTrait> traitsField;

  /** One or more types of the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "types")
  private java.util.List<io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT> typesField;

  /** The alternative unique identifier of the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier of the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;
}
