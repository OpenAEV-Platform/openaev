package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectJobTrigger extends OcsfObject {
  /**
   * Event identifiers that pertain to the job trigger. Should be populated when the <code>Event (3)
   * </code> type is specified.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "event_codes")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> eventCodesField;

  /** The time when the job was last run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastRunTimeDtField;

  /** The time when the job was last run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastRunTimeField;

  /**
   * A collection of log systems or components that pertain to the job trigger. Should be populated
   * when the <code>Event (3)</code> type is specified.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_sources")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> logSourcesField;

  /** The time when the job will next be run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "next_run_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT nextRunTimeDtField;

  /** The time when the job will next be run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "next_run_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT nextRunTimeField;

  /**
   * The list of properties associated with the trigger.<br>
   * Can be used to describe time boundaries of the job, amount of repetitions or when the job
   * should be activated or expired.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "properties")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject>
      propertiesField;

  /**
   * The job trigger type, normalized to the caption of the <code>type_id</code> value. In the case
   * of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The job trigger type, i.e. the condition that must be met for the job to perform its action.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The user that pertains to the job trigger. Can be populated when the <code>Event (3)</code>
   * type that bounds to specific user is used.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;
}
