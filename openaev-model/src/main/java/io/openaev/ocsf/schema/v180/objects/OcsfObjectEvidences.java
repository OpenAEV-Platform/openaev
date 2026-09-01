package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEvidences extends OcsfObject {
  /**
   * Describes details about the user/role/process that was the source of the activity that
   * triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectActor actorField;

  /**
   * Describes details about the API call associated to the activity that triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectApi apiField;

  /**
   * Describes details about the network connection associated to the activity that triggered the
   * detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "connection_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkConnectionInfo connectionInfoField;

  /**
   * Describes details about the container associated to the activity that triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectContainer containerField;

  /**
   * Additional evidence data that is not accounted for in the specific evidence attributes.<code>
   *  Use only when absolutely necessary.</code>
   */
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  /**
   * Describes details about the database associated to the activity that triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "database")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDatabase databaseField;

  /**
   * Describes details about the databucket associated to the activity that triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "databucket")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDatabucket databucketField;

  /**
   * An addressable device, computer system or host associated to the activity that triggered the
   * detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice deviceField;

  /**
   * Describes details about the destination of the network activity that triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dst_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint dstEndpointField;

  /** The email object associated to the activity that triggered the detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "email")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectEmail emailField;

  /** Describes details about the file associated to the activity that triggered the detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  /**
   * Describes details about the http request associated to the activity that triggered the
   * detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_request")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpRequest httpRequestField;

  /**
   * Describes details about the http response associated to the activity that triggered the
   * detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_response")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpResponse httpResponseField;

  /** Describes details about the JA4+ fingerprints that triggered the detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ja4_fingerprint_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectJa4Fingerprint>
      ja4FingerprintListField;

  /**
   * Describes details about the scheduled job that was associated with the activity that triggered
   * the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "job")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectJob jobField;

  /**
   * The naming convention or type identifier of the evidence associated with the security
   * detection. For example, the <code>@odata.type</code> from Microsoft Graph Alerts V2 or <code>
   * display_name</code> from CrowdStrike Falcon Incident Behaviors.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * Describes details about the process associated to the activity that triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess processField;

  /**
   * Describes details about the DNS query associated to the activity that triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDnsQuery queryField;

  /** Describes details about the registry key that triggered the detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_key")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRegKey regKeyField;

  /** Describes details about the registry value that triggered the detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_value")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRegValue regValueField;

  /**
   * Describes details about the cloud resources directly related to activity that triggered the
   * detection. For resources impacted by the detection, use <code>Affected Resources</code> at the
   * top-level of the finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resources")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectResourceDetails>
      resourcesField;

  /**
   * Describes details about the script that was associated with the activity that triggered the
   * detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "script")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectScript scriptField;

  /** Describes details about the source of the network activity that triggered the detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint srcEndpointField;

  /**
   * Describes details about the Transport Layer Security (TLS) activity that triggered the
   * detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tls")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTls tlsField;

  /**
   * The unique identifier of the evidence associated with the security detection. For example, the
   * <code>activity_id</code> from CrowdStrike Falcon Alerts or <code>behavior_id</code> from
   * CrowdStrike Falcon Incident Behaviors.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The URL object that pertains to the event or object associated to the activity that triggered
   * the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl urlField;

  /**
   * Describes details about the user that was the target or somehow else associated with the
   * activity that triggered the detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser userField;

  /** The normalized verdict of the evidence associated with the security detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "verdict")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT verdictField;

  /**
   * The normalized verdict (or status) ID of the evidence associated with the security detection.
   * For example, Microsoft Graph Security Alerts contain a <code>verdict</code> enumeration for
   * each type of <code>evidence</code> associated with the Alert. This is typically set by an
   * automated investigation process or an analyst/investigator assigned to the finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "verdict_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT verdictIdField;

  /** Describes details about the Windows service that triggered the detection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "win_service")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectWinService winServiceField;
}
