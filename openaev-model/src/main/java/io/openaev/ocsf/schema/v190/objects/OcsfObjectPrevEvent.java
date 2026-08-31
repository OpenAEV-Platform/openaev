package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPrevEvent extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint fingerprintField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT typeUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
