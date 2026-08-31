package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAdvisory extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "avg_timespan")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTimespan avgTimespanField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bulletin")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT bulletinField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "classification")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classificationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "install_state")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT installStateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "install_state_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT installStateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_superseded")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isSupersededField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOs osField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "references")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> referencesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_cves")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectCve> relatedCvesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_cwes")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectCwe> relatedCwesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT sizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT titleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
