package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPackage extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "architecture")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT architectureField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpe_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpeNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "epoch")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT epochField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint hashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "license")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT licenseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "license_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT licenseUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "package_manager")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT packageManagerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "package_manager_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT packageManagerUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "purl")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT purlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "release")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT releaseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
