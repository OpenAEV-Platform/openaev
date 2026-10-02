package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectProcessEntity extends OcsfObject {
  /**
   * The full command line used to launch an application, service, process, or job. For example:
   * <code>ssh user@10.0.0.10</code>. If the command line is unavailable or missing, the empty
   * string <code>''</code> is to be used.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cmdLineField;

  /**
   * A unique process identifier that can be assigned deterministically by multiple system data
   * producers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUuidT cpidField;

  /** The time when the process was created/started. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the process was created/started. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The friendly name of the process, for example: <code>Notepad++</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The process file path. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  /**
   * The process identifier, as reported by the operating system. Process ID (PID) is a number used
   * by the operating system to uniquely identify an active process.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT pidField;

  /**
   * A unique identifier for this process assigned by the producer (tool). Facilitates correlation
   * of a process event with other events for that process.
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
