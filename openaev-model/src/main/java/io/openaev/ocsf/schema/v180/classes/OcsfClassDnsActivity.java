package io.openaev.ocsf.schema.v180.classes;

import io.openaev.ocsf.schema.OcsfClass;

@lombok.Getter
public class OcsfClassDnsActivity extends OcsfClass {
  /** The normalized caption of <code>action_id</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "action")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT actionField;

  /**
   * The action taken by a control or other policy-based system leading to an outcome or
   * disposition. An unknown action may still correspond to a known disposition. Refer to <code>
   * disposition_id</code> for the outcome of the action.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "action_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT actionIdField;

  /** The normalized identifier of the activity that triggered the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT activityIdField;

  /** The event activity name, as defined by the activity_id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT activityNameField;

  /**
   * The actor object describes details about the user/role/process that was the source of the
   * activity. Note that this is not the threat actor of a campaign but may be part of a campaign.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectActor actorField;

  /** The Domain Name System (DNS) answers. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "answers")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDnsAnswer> answersField;

  /** Describes details about a typical API (Application Programming Interface) call. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectApi apiField;

  /**
   * The network application name identified by tools such as NBAR or App ID (e.g., youtube,
   * facebook, webex). This represents a specific network application that uses standard protocols
   * (such as https or quic) to deliver its service.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT appNameField;

  /**
   * The application-layer (Layer 7) protocol name identified by deep packet inspection or packet
   * parsing (e.g., <code>https</code>, <code>quic</code>, <code>ssh</code>, <code>dns</code>),
   * expressed as an IANA-registered service name from the IANA Service Name and Transport Protocol
   * Port Number Registry.
   *
   * <p><b>Note:</b> Port numbers alone are not always a reliable indicator of the actual
   * application protocol in use.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_protocol_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT appProtocolNameField;

  /**
   * An array of MITRE ATT&CK® objects describing identified tactics, techniques & sub-techniques.
   * The objects are compatible with MITRE ATLAS™ tactics, techniques & sub-techniques.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAttack> attacksField;

  /**
   * Provides details about an authorization, such as authorization outcome, and any associated
   * policies related to the activity/event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthorization>
      authorizationsField;

  /** The event category name, as defined by category_uid value: <code>Network Activity</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT categoryNameField;

  /** The category unique identifier of the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT categoryUidField;

  /** The event class name, as defined by class_uid value: <code>DNS Activity</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classNameField;

  /** The unique identifier of a class. A class describes the attributes available in an event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT classUidField;

  /** Describes details about the Cloud environment where the event or finding was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectCloud cloudField;

  /**
   * The confidence, normalized to the caption of the confidence_id value. In the case of 'Other',
   * it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT confidenceField;

  /**
   * The normalized confidence refers to the accuracy of the rule that created the finding. A rule
   * with a low confidence means that the finding scope is wide and may create finding reports that
   * may not be malicious in nature.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT confidenceIdField;

  /** The confidence score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT confidenceScoreField;

  /** The network connection information. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "connection_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkConnectionInfo connectionInfoField;

  /**
   * The number of times that events in the same logical group occurred during the event
   * <strong>Start Time</strong> to <strong>End Time</strong> period.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT countField;

  /**
   * The cumulative (running total) network traffic aggregated from the start of a flow or session.
   * Use when reporting: (1) total accumulated bytes/packets since flow initiation, (2) combined
   * aggregation models where both incremental deltas and running totals are reported together
   * (populate both <code>traffic</code> for the delta and this attribute for the cumulative total),
   * or (3) final summary metrics when a long-lived connection closes. This represents the sum of
   * all activity from flow start to the current observation, not a delta or point-in-time value.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cumulative_traffic")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkTraffic cumulativeTrafficField;

  /** An addressable device, computer system or host. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice deviceField;

  /**
   * The disposition name, normalized to the caption of the disposition_id value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dispositionField;

  /**
   * Describes the outcome or action taken by a security control, such as access control checks,
   * malware detections or various types of policy violations.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT dispositionIdField;

  /** The responder (server) in a network connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dst_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint dstEndpointField;

  /**
   * The event duration or aggregate time, the amount of time the event covers from <code>start_time
   * </code> to <code>end_time</code> in milliseconds.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT durationField;

  /**
   * The end time of a time period, or the time of the most recent event included in the aggregate
   * event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /**
   * The end time of a time period, or the time of the most recent event included in the aggregate
   * event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT endTimeField;

  /**
   * The additional information from an external data source, which is associated with the event or
   * a finding. For example add location information for the IP address in the DNS answers:<code>
   * [{"name": "answers.ip", "value": "92.24.47.250", "type": "location", "data": {"city": "Socotra", "continent": "Asia", "coordinates": [-25.4153, 17.0743], "country": "YE", "desc": "Yemen"}}]
   * </code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "enrichments")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectEnrichment> enrichmentsField;

  /** The firewall rule that pertains to the control that triggered the event, if applicable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "firewall_rule")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFirewallRule firewallRuleField;

  /**
   * Indicates that the event is considered to be an alertable signal. Should be set to <code>true
   * </code> if <code>disposition_id = Alert</code> among other dispositions, and/or <code>
   * risk_level_id</code> or <code>severity_id</code> of the event is elevated. Not all control
   * events will be alertable, for example if <code>disposition_id = Exonerated</code> or <code>
   * disposition_id = Allowed</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_alert")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isAlertField;

  /** A list of the JA4+ network fingerprints. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ja4_fingerprint_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectJa4Fingerprint>
      ja4FingerprintListField;

  /**
   * The Load Balancer object contains information related to the device that is distributing
   * incoming traffic to specified destinations.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "load_balancer")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLoadBalancer loadBalancerField;

  /** A list of Malware objects, describing details about the identified malware. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectMalware> malwareField;

  /** Describes details about the scan job that identified malware on the target system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware_scan_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectMalwareScanInfo malwareScanInfoField;

  /** The description of the event/finding, as defined by the source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageField;

  /** The metadata associated with the event or a finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "metadata")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectMetadata metadataField;

  /**
   * The network endpoint that observes or inspects network traffic as a third-party system, used
   * when the observer is neither the source nor the destination of the communication (when <code>
   * observation_point_id</code> = 3). Examples include network taps, span ports, inline security
   * devices, or packet capture systems that monitor traffic between other endpoints.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_observation_point")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint
      networkObservationPointField;

  /** The observables associated with the event or a finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observables")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectObservable> observablesField;

  /**
   * Indicates whether the source network endpoint, destination network endpoint, or neither served
   * as the observation point for the activity. The value is normalized to the caption of the <code>
   * observation_point_id</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_point")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT observationPointField;

  /**
   * The normalized identifier of the observation point. The observation point identifier indicates
   * whether the source network endpoint, destination network endpoint, or neither served as the
   * observation point for the activity.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_point_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT observationPointIdField;

  /**
   * The OSINT (Open Source Intelligence) object contains details related to an indicator such as
   * the indicator itself, related indicators, geolocation, registrar information, subdomains,
   * analyst commentary, and other contextual information. This information can be used to further
   * enrich a detection or finding by providing decisioning support to other analysts and engineers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "osint")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectOsint> osintField;

  /** The list of packet objects describing captured network packets. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packet_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectPacket> packetListField;

  /**
   * The policy that pertains to the control that triggered the event, if applicable. For example
   * the name of an anti-malware policy or an access control policy.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectPolicy policyField;

  /** The connection information from the proxy server to the remote server. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_connection_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkConnectionInfo
      proxyConnectionInfoField;

  /** The proxy (server) in a network connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkProxy proxyEndpointField;

  /** The proxy (server) in a network connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkProxy proxyField;

  /** The HTTP Request from the proxy server to the remote server. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_http_request")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpRequest proxyHttpRequestField;

  /** The HTTP Response from the remote server to the proxy server. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_http_response")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpResponse proxyHttpResponseField;

  /** The TLS protocol negotiated between the proxy server and the remote server. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_tls")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTls proxyTlsField;

  /**
   * The network traffic refers to the amount of data moving across a network, from proxy to remote
   * server at a given point of time.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_traffic")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkTraffic proxyTrafficField;

  /** The Domain Name System (DNS) query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDnsQuery queryField;

  /** The Domain Name System (DNS) query time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT queryTimeDtField;

  /** The Domain Name System (DNS) query time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT queryTimeField;

  /** The raw event/finding data as received from the source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT rawDataField;

  /** The hash, which describes the content of the raw_data field. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_hash")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint rawDataHashField;

  /** The size of the raw data which was transformed into an OCSF event, in bytes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT rawDataSizeField;

  /**
   * The DNS server response code, normalized to the caption of the rcode_id value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rcode")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT rcodeField;

  /**
   * The normalized identifier of the DNS server response code. See <a target='_blank'
   * href='https://datatracker.ietf.org/doc/html/rfc6895'>RFC-6895</a>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rcode_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT rcodeIdField;

  /** The Domain Name System (DNS) response time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "response_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT responseTimeDtField;

  /** The Domain Name System (DNS) response time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "response_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT responseTimeField;

  /** Describes the risk associated with the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_details")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskDetailsField;

  /** The risk level, normalized to the caption of the risk_level_id value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskLevelField;

  /** The normalized risk level id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  /** The risk score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskScoreField;

  /**
   * The event/finding severity, normalized to the caption of the <code>severity_id</code> value. In
   * the case of 'Other', it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT severityField;

  /**
   * The normalized identifier of the event/finding severity.The normalized severity is a
   * measurement the effort and expense required to manage and resolve an event or incident. Smaller
   * numerical values represent lower impact events, and larger numerical values represent higher
   * impact events.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT severityIdField;

  /** The initiator (client) of the network connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint srcEndpointField;

  /**
   * The start time of a time period, or the time of the least recent event included in the
   * aggregate event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /**
   * The start time of a time period, or the time of the least recent event included in the
   * aggregate event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT startTimeField;

  /**
   * The event status code, as reported by the event source.<br>
   * <br>
   * For example, in a Windows Failed Authentication event, this would be the value of 'Failure
   * Code', e.g. 0x18.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusCodeField;

  /** The status detail contains additional information about the event/finding outcome. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusDetailField;

  /**
   * The event status, normalized to the caption of the status_id value. In the case of 'Other', it
   * is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusField;

  /** The normalized identifier of the event status. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT statusIdField;

  /** The normalized event occurrence time or the finding creation time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT timeDtField;

  /** The normalized event occurrence time or the finding creation time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT timeField;

  /**
   * The number of minutes that the reported event <code>time</code> is ahead or behind UTC, in the
   * range -1,080 to +1,080.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "timezone_offset")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT timezoneOffsetField;

  /** The Transport Layer Security (TLS) attributes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tls")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTls tlsField;

  /**
   * The network traffic for this observation period. Use when reporting: (1) delta values
   * (bytes/packets transferred since the last observation), (2) instantaneous measurements at a
   * specific point in time, or (3) standalone single-event metrics. This attribute represents a
   * point-in-time measurement or incremental change, not a running total. For accumulated totals
   * across multiple observations or the lifetime of a flow, use <code>cumulative_traffic</code>
   * instead.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "traffic")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkTraffic trafficField;

  /** The event/finding type name, as defined by the type_uid. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeNameField;

  /**
   * The event/finding type ID. It identifies the event's semantics and structure. The value is
   * calculated by the logging system as: <code>class_uid * 100 + activity_id</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT typeUidField;

  /**
   * The attributes that are not mapped to the event schema. The names and values of those
   * attributes are specific to the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "unmapped")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectObject unmappedField;
}
