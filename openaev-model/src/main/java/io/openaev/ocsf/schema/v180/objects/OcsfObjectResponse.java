package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectResponse extends OcsfObject {
  /** The numeric response sent to a request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT codeField;

  /**
   * When working with containerized applications, the set of containers which write to the standard
   * the output of a particular logging driver. For example, this may be the set of containers
   * involved in handling api requests and responses for a containerized application.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "containers")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectContainer> containersField;

  /** The additional data that is associated with the api response. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  /** Error Code */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT errorField;

  /** Error Message */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error_message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT errorMessageField;

  /** The communication flags that are associated with the api response. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> flagsField;

  /** The description of the event/finding, as defined by the source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageField;
}
