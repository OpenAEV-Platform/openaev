package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEdge extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_directed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isDirectedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "relation")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT relationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "source")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sourceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "target")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT targetField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
