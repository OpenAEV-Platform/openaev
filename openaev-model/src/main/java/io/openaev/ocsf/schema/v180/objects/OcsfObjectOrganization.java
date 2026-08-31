package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectOrganization extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ou_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ouNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ou_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ouUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
