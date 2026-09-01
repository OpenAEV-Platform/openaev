package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAnalysisTarget extends OcsfObject {
  /**
   * The specific name or identifier of the analysis target, such as the username of a User Account,
   * the name of a Kubernetes Cluster, the identifier of a Network Namespace, or the name of an
   * Application Component.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The category of the analysis target, such as User Account, Kubernetes Cluster, Network
   * Namespace, or Application Component.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;
}
