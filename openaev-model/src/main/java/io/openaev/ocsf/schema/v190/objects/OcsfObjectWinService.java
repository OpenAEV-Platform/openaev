package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectWinService {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile serviceFileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "load_order_group")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT loadOrderGroupField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hosting_process")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcessEntity hostingProcessField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_error_control_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serviceErrorControlIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_start_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serviceStartTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_dependencies")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceDependenciesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_start_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceStartTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serviceTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_dll_file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile serviceDllFileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_error_control")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceErrorControlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_category")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceCategoryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cmdLineField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_category_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serviceCategoryIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject tagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT labelsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_start_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceStartNameField;
}
