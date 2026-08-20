package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectGraph {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_language_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT queryLanguageIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_directed")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isDirectedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "edges")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEdge edgesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_language")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT queryLanguageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "nodes")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNode nodesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
