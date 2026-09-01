package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRegValue extends OcsfObject {
  /**
   * The data of the registry value. Where the value type is known, implementers should instead use
   * a type-specific attribute, i.e. <code>reg_binary_data</code>, <code>reg_integer_data</code>,
   * <code>reg_string_data</code>, or <code>reg_string_list_data</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v190.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  /**
   * The indication of whether the value is from a default value name. For example, the value name
   * could be missing.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_default")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isDefaultField;

  /** The indication of whether the object is part of the operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_system")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isSystemField;

  /** The time when the registry value was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the registry value was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The name of the registry value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The full path to the registry key, where the value is located. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  /**
   * The data of the registry value when <code>type_id</code> is <code>REG_BINARY</code> or <code>
   * REG_NONE</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_binary_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBytestringT regBinaryDataField;

  /**
   * The data of the registry value when <code>type_id</code> is <code>REG_DWORD</code>, <code>
   * REG_DWORD_BIG_ENDIAN</code>, or <code>REG_QWORD</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_integer_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT regIntegerDataField;

  /**
   * The data of the registry value when <code>type_id</code> is <code>REG_SZ</code>, <code>
   * REG_EXPAND_SZ</code>, or <code>REG_LINK</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_string_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT regStringDataField;

  /** The data of the registry value when <code>type_id</code> is <code>REG_MULTI_SZ</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_string_list_data")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      regStringListDataField;

  /** A string representation of the value type as specified in Windows Registry Value Types. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The value type ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;
}
