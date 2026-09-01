package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRegKey extends OcsfObject {
  /** The indication of whether the object is part of the operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_system")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSystemField;

  /** The time when the registry key was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the registry key was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The full path to the registry key. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  /** The security descriptor of the registry key. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "security_descriptor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT securityDescriptorField;
}
