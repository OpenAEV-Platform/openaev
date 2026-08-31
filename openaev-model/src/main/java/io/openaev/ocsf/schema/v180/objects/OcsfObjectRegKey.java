package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRegKey extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_system")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSystemField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "security_descriptor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT securityDescriptorField;
}
