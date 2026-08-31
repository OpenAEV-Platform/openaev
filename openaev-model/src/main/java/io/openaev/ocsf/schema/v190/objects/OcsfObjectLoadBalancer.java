package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLoadBalancer extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "classification")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classificationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT codeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dst_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint dstEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "endpoint_connections")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectEndpointConnection>
      endpointConnectionsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "error_message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT errorMessageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIpT ipField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT messageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "metrics")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectMetric> metricsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusDetailField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
