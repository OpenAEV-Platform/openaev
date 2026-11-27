package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectProcess extends OcsfObject {
  /**
   * The autonomous AI agent that this process is running or hosting, if applicable. Populate when
   * the process serves as the runtime environment for an AI agent (e.g., a Python process executing
   * a LangChain agent or hosting an MCP server). When a process hosts multiple agents and the
   * activity cannot be attributed to one specifically, use <code>hosted_ai_agent_list</code>
   * instead.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_agent")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiAgent aiAgentField;

  /**
   * An array of Process Entities describing the extended parentage of this process object. Direct
   * parent information should be expressed through the <code>parent_process</code> attribute. The
   * first array element is the direct parent of this process object. Subsequent list elements go up
   * the process parentage hierarchy. That is, the array is sorted from newest to oldest process. It
   * is recommended to only populate this field for the top-level process object.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ancestry")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectProcessEntity>
      ancestryField;

  /** The audit user assigned at login by the audit subsystem. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "auid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT auidField;

  /**
   * The call stack of the thread identified by <code>ptid</code>, captured at the time of the
   * activity, as an ordered list of frames starting with the most recent call. Each frame is a
   * return address written as the module path, followed by <code>!</code> and the symbol name when
   * the symbol is known, then <code>+</code> and the offset. Numbers are decimal unless they end
   * with <code>h</code>, which marks them as hexadecimal. For example: <code>
   * C:\Windows\System32\ntdll.dll+9d4c4h</code>, <code>
   * C:\Windows\System32\ntdll.dll!NtOpenProcess+14h</code> or <code>
   * /usr/lib/x86_64-linux-gnu/libc.so.6!malloc+141</code>. A frame that is not backed by a loaded
   * module is written as the address alone, for example <code>00000216A4C10F2Bh</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "call_stack")
  private java.util.List<io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT> callStackField;

  /**
   * The full command line used to launch an application, service, process, or job. For example:
   * <code>ssh user@10.0.0.10</code>. If the command line is unavailable or missing, the empty
   * string <code>''</code> is to be used.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT cmdLineField;

  /**
   * The information describing an instance of a container. A container is a prepackaged, portable
   * system image that runs isolated on an existing system using a container runtime like
   * containerd.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectContainer containerField;

  /**
   * A unique process identifier that can be assigned deterministically by multiple system data
   * producers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeUuidT cpidField;

  /** The time when the process was created/started. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the process was created/started. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The effective group under which this process is running. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "egid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT egidField;

  /** Environment variables associated with the process. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "environment_variables")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectEnvironmentVariable>
      environmentVariablesField;

  /** The effective user under which this process is running. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "euid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT euidField;

  /** The process file object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectFile fileField;

  /** The group under which this process is running. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectGroup groupField;

  /**
   * The list of autonomous AI agents hosted by this process. Use when a single process hosts
   * multiple agents (e.g., an IDE running several coding agents within the same host process) and
   * the producer cannot attribute a specific activity to a single agent. When attribution is
   * available, populate the singular <code>ai_agent</code> attribute instead.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hosted_ai_agent_list")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiAgent>
      hostedAiAgentListField;

  /** The Windows services that this process is hosting. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hosted_services")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectWinService>
      hostedServicesField;

  /**
   * The process integrity level, normalized to the caption of the integrity_id value. In the case
   * of 'Other', it is defined by the event source (Windows only).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "integrity")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT integrityField;

  /** The normalized identifier of the process integrity level (Windows only). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "integrity_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT integrityIdField;

  /**
   * The lineage of the process, represented by a list of paths for each ancestor process. For
   * example: <code>['/usr/sbin/sshd', '/usr/bin/bash', '/usr/bin/whoami']</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "lineage")
  private java.util.List<io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeFilePathT> lineageField;

  /** The list of loaded module names. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "loaded_modules")
  private java.util.List<io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT>
      loadedModulesField;

  /** The friendly name of the process, for example: <code>Notepad++</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT nameField;

  /**
   * If running under a process namespace (such as in a container), the process identifier within
   * that process namespace.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace_pid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT namespacePidField;

  /**
   * The parent process of this process object. It is recommended to only populate this field for
   * the top-level process object, to prevent deep nesting. Additional ancestry information can be
   * supplied in the <code>ancestry</code> attribute.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "parent_process")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectProcess parentProcessField;

  /** The process file path. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT pathField;

  /**
   * The process identifier, as reported by the operating system. Process ID (PID) is a number used
   * by the operating system to uniquely identify an active process.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT pidField;

  /**
   * The identifier of the process thread associated with the event, as returned by the operating
   * system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ptid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT ptidField;

  /**
   * The name of the containment jail (i.e., sandbox). For example, hardened_ps, high_security_ps,
   * oracle_ps, netsvcs_ps, or default_ps.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sandbox")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT sandboxField;

  /** The user session under which this process is running. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectSession sessionField;

  /** The time when the process was terminated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "terminated_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT terminatedTimeDtField;

  /** The time when the process was terminated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "terminated_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT terminatedTimeField;

  /**
   * The identifier of the thread associated with the event, as returned by the operating system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT tidField;

  /**
   * The User Account Control elevation type of the access token that the process runs with,
   * normalized to the caption of the <code>token_elevation_type_id</code> value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "token_elevation_type")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT tokenElevationTypeField;

  /**
   * The normalized identifier of the User Account Control elevation type of the access token that
   * the process runs with. The values match the Windows <code>TOKEN_ELEVATION_TYPE</code>
   * enumeration.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "token_elevation_type_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT tokenElevationTypeIdField;

  /**
   * A unique identifier for this process assigned by the producer (tool). Facilitates correlation
   * of a process event with other events for that process.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT uidNumericField;

  /** The user under which this process is running. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectUser userField;

  /** The working directory of a process. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "working_directory")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT workingDirectoryField;

  /**
   * An unordered collection of zero or more name/value pairs that represent a process extended
   * attribute.
   *
   * <p>For example: process security context attributes, process resource limits,
   * container-specific attributes, or platform-specific process metadata not covered by standard
   * OCSF attributes.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "xattributes")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v1100.JsonNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeJsonT xattributesField;
}
