package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLongString extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_truncated")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isTruncatedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "untruncated_size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT untruncatedSizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT valueField;
}
