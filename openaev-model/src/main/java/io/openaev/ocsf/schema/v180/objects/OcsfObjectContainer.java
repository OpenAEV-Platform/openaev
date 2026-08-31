package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectContainer extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hash")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint hashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "image")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectImage imageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> labelsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_driver")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT networkDriverField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "orchestrator")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT orchestratorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pod_uuid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUuidT podUuidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "runtime")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT runtimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tag")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT tagField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject> tagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
