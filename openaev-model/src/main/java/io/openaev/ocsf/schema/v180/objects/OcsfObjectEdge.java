package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEdge extends OcsfObject {
  /** Additional data about the edge such as weight, distance, or custom properties. */
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  /** Indicates whether the edge is (<code>true</code>) or undirected (<code>false</code>). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_directed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isDirectedField;

  /** The human-readable name or label for the edge. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The type of relationship between nodes (e.g. is-attached-to , depends-on, etc). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "relation")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT relationField;

  /** The unique identifier of the node where the edge originates. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "source")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sourceField;

  /** The unique identifier of the node where the edge terminates. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "target")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT targetField;

  /** Unique identifier of the edge. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
