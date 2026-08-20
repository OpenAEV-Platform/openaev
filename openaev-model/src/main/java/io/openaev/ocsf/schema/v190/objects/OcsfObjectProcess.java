package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectProcess {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hosted_ai_agent_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiAgent hostedAiAgentListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer containerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ptid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT ptidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile fileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "lineage")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFilePathT lineageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "integrity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT integrityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "working_directory")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT workingDirectoryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSession sessionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sandbox")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sandboxField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUuidT cpidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "loaded_modules")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT loadedModulesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_agent")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiAgent aiAgentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "euid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT euidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "environment_variables")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEnvironmentVariable
      environmentVariablesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT pidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hosted_services")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectWinService hostedServicesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "integrity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT integrityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "egid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT egidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "auid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT auidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "terminated_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT terminatedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cmdLineField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT tidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "terminated_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT terminatedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ancestry")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcessEntity ancestryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace_pid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT namespacePidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup groupField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "xattributes")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectObject xattributesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "parent_process")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcess parentProcessField;
}
