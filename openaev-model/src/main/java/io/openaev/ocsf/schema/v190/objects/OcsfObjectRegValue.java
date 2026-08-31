package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRegValue extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_default")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isDefaultField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_system")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isSystemField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_binary_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBytestringT regBinaryDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_integer_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT regIntegerDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_string_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT regStringDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_string_list_data")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      regStringListDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;
}
