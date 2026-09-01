package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectKbArticle extends OcsfObject {
  /** The average time to patch. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "avg_timespan")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTimespan avgTimespanField;

  /** The kb article bulletin identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bulletin")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT bulletinField;

  /** The vendors classification of the kb article. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "classification")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classificationField;

  /** The date the kb article was released by the vendor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The date the kb article was released by the vendor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The install state of the kb article. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "install_state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT installStateField;

  /** The normalized install state ID of the kb article. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "install_state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT installStateIdField;

  /** The kb article has been replaced by another. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_superseded")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSupersededField;

  /** The operating system the kb article applies. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOs osField;

  /** The product details the kb article applies. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProduct productField;

  /** The severity of the kb article. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT severityField;

  /** The size in bytes for the kb article. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  /** The kb article link from the source vendor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /** The title of the kb article. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT titleField;

  /** The unique identifier for the kb article. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
