package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLoadBalancer extends OcsfObject {
  /** The request classification as defined by the load balancer. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "classification")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classificationField;

  /**
   * The numeric response status code detailing the connection from the load balancer to the
   * destination target.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT codeField;

  /** The destination to which the load balancer is distributing traffic. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dst_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint dstEndpointField;

  /** An object detailing the load balancer connection attempts and responses. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "endpoint_connections")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectEndpointConnection>
      endpointConnectionsField;

  /** The load balancer error message. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error_message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT errorMessageField;

  /**
   * The IP address of the load balancer node that handled the client request. Note: the load
   * balancer may have other IP addresses, and this is not an IP address of the target/distribution
   * endpoint - see <code>dst_endpoint</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT ipField;

  /** The load balancer message. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageField;

  /** General purpose metrics associated with the load balancer. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "metrics")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectMetric> metricsField;

  /** The name of the load balancer. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The status detail contains additional status information about the load balancer distribution
   * event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusDetailField;

  /** The unique identifier for the load balancer. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
