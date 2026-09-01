package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectGraph extends OcsfObject {
  /** The graph description - provides additional details about the graph's purpose and contents. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /**
   * The edges/connections between nodes in the graph - contains the collection of <code>edge</code>
   * objects defining relationships between nodes.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "edges")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectEdge> edgesField;

  /** Indicates if the graph is directed (<code>true</code>) or undirected (<code>false</code>). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_directed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isDirectedField;

  /** The graph name - a human readable identifier for the graph. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The nodes/vertices of the graph - contains the collection of <code>node</code> objects that
   * make up the graph.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "nodes")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectNode> nodesField;

  /**
   * The graph query language, normalized to the caption of the <code>query_language_id</code>
   * value.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_language")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT queryLanguageField;

  /**
   * The normalized identifier of a graph query language that can be used to interact with the
   * graph.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_language_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT queryLanguageIdField;

  /** The graph type. Typically useful to represent the specific type of graph that is used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** Unique identifier of the graph - a unique ID to reference this specific graph. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
