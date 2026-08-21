package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectEvidences extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_agent")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiAgent aiAgentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_value")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRegValue regValueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_key")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRegKey regKeyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsQuery queryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEmail emailField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "resources")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectResourceDetails resourcesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ja4_fingerprint_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectJa4Fingerprint ja4FingerprintListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_request")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpRequest httpRequestField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice deviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "job")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectJob jobField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer containerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dst_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint dstEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_response")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpResponse httpResponseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "database")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDatabase databaseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "verdict")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT verdictField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUrl urlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectActor actorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "win_service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectWinService winServiceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcess processField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "verdict_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT verdictIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tls")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTls tlsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApi apiField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "databucket")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDatabucket databucketField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile fileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint srcEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "connection_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkConnectionInfo connectionInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "script")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectScript scriptField;
}
