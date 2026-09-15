package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDownloadInfo extends OcsfObject {
  /**
   * The URL that referred to <code>src_url</code>. This is typically the URL of a web page
   * containing a download link.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "referrer")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT referrerField;

  /** Information about the network endpoint from which the file was downloaded. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint srcEndpointField;

  /** The URL from which the file was downloaded. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  /**
   * The download type, normalized to the caption of the <code>type_id</code> value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The download type ID. The values correspond to the five permitted values of the <code>ZoneId
   * </code> property in the Mark of the Web metadata of a downloaded file on Windows. Note however
   * that each numeric value is 1 greater than its <code>ZoneId</code> equivalent.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;
}
