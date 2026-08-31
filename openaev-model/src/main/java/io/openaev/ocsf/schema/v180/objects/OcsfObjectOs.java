package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectOs extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "build")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT buildField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "country")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT countryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpe_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cpeNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_bits")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT cpuBitsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "edition")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT editionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "kernel_release")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT kernelReleaseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "lang")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT langField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sp_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT spNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sp_ver")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT spVerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
