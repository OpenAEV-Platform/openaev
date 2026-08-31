package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCve extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cvss")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectCvss> cvssField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cwe")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCwe cweField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cwe_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cweUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cwe_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT cweUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "epss")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEpss epssField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "references")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> referencesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_cwes")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectCwe> relatedCwesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT titleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
