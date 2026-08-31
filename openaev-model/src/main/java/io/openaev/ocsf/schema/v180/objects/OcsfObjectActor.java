package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectActor extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT appNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT appUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthorization>
      authorizationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "idp")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectIdp idpField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "invoked_by")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT invokedByField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess processField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSession sessionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser userField;
}
