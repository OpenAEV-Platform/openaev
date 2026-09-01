package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFindingInfo extends OcsfObject {
  /**
   * The analytic technique used to analyze and derive insights from the data or information that
   * led to the finding or conclusion.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "analytic")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalytic analyticField;

  /**
   * An Attack Graph describes possible routes an attacker could take through an environment. It
   * describes relationships between resources and their findings, such as malware detections,
   * vulnerabilities, misconfigurations, and other security actions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attack_graph")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGraph attackGraphField;

  /**
   * The <a target='_blank' href='https://attack.mitre.org'>MITRE ATT&CK®</a> technique and
   * associated tactics related to the finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAttack> attacksField;

  /** The time when the finding was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the finding was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** A list of data sources utilized in generation of the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_sources")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      dataSourcesField;

  /** The description of the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /**
   * The time when the finding was first observed. e.g. The time when a vulnerability was first
   * observed.
   *
   * <p>It can differ from the <code>created_time</code> timestamp, which reflects the time this
   * finding was created.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  /**
   * The time when the finding was first observed. e.g. The time when a vulnerability was first
   * observed.
   *
   * <p>It can differ from the <code>created_time</code> timestamp, which reflects the time this
   * finding was created.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  /**
   * The <a target='_blank'
   * href='https://www.lockheedmartin.com/en-us/capabilities/cyber/cyber-kill-chain.html'>Cyber Kill
   * Chain®</a> provides a detailed description of each phase and its associated activities within
   * the broader context of a cyber attack.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kill_chain")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKillChainPhase>
      killChainField;

  /**
   * The time when the finding was most recently observed. e.g. The time when a vulnerability was
   * most recently observed.
   *
   * <p>It can differ from the <code>modified_time</code> timestamp, which reflects the time this
   * finding was last modified.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  /**
   * The time when the finding was most recently observed. e.g. The time when a vulnerability was
   * most recently observed.
   *
   * <p>It can differ from the <code>modified_time</code> timestamp, which reflects the time this
   * finding was last modified.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  /** The time when the finding was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the finding was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** Details about the product that reported the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProduct productField;

  /** The unique identifier of the product that reported the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT productUidField;

  /** Other analytics related to this finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_analytics")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalytic>
      relatedAnalyticsField;

  /** Number of related events or findings. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_events_count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT relatedEventsCountField;

  /**
   * Describes events and/or other findings related to the finding as identified by the security
   * product. Note that these events may or may not be in OCSF.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_events")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectRelatedEvent>
      relatedEventsField;

  /** The URL pointing to the source of the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /** The list of tags; <code>{key:value}</code> pairs associated with the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject> tagsField;

  /** A title or a brief phrase summarizing the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT titleField;

  /** The list of key traits or characteristics extracted from the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "traits")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectTrait> traitsField;

  /** One or more types of the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "types")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> typesField;

  /** The alternative unique identifier of the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier of the reported finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
