package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectJob extends OcsfObject {
  /** The number of bytes the job read or examined while performing its work. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_processed")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT bytesProcessedField;

  /**
   * The number of bytes the job committed to the destination, after any compression or
   * deduplication was applied.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_written")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT bytesWrittenField;

  /** The job command line. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT cmdLineField;

  /** The time when the job was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the job was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The description of the job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT descField;

  /** The file that pertains to the job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectFile fileField;

  /** An array of actions that will be performed by the job when certain conditions are met. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "job_actions")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectJobAction> jobActionsField;

  /**
   * An array of conditions or events that, when met, will initiate the job to perform specified
   * actions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "job_triggers")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectJobTrigger>
      jobTriggersField;

  /** The time when the job was last run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT lastRunTimeDtField;

  /** The time when the job was last run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT lastRunTimeField;

  /** The name of the job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT nameField;

  /** The time when the job will next be run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "next_run_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT nextRunTimeDtField;

  /** The time when the job will next be run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "next_run_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT nextRunTimeField;

  /** The units of work the job has completed so far, counted in <code>progress_unit</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "progress_current")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT progressCurrentField;

  /**
   * The total units of work the job is expected to perform, counted in <code>progress_unit</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "progress_total")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT progressTotalField;

  /**
   * The unit that the job's <code>progress_current</code> and <code>progress_total</code> are
   * counted in, normalized to the caption of the <code>progress_unit_id</code> value. In the case
   * of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "progress_unit")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT progressUnitField;

  /**
   * The normalized identifier of the unit that the job's <code>progress_current</code> and <code>
   * progress_total</code> are counted in.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "progress_unit_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT progressUnitIdField;

  /** The name of the queue that the job was dispatched to. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "queue_name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT queueNameField;

  /** The run state of the job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT runStateField;

  /** The run state ID of the job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT runStateIdField;

  /** The rate at which the job processed data, in bytes per second. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "throughput")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT throughputField;

  /**
   * The job type, normalized to the caption of the <code>type_id</code> value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT typeField;

  /** The job type, i.e. the mechanism that executes the job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The unique job identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;

  /** The user that created the job. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectUser userField;
}
