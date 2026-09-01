package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectOsint extends OcsfObject {
  /** Any pertinent DNS answers information related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "answers")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDnsAnswer> answersField;

  /**
   * MITRE ATT&CK Tactics, Techniques, and/or Procedures (TTPs) pertinent to an indicator or OSINT
   * analysis.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAttack> attacksField;

  /** Any pertinent autonomous system information related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "autonomous_system")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectAutonomousSystem autonomousSystemField;

  /**
   * The campaign object describes details about the campaign that was the source of the activity.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "campaign")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectCampaign campaignField;

  /** Categorizes the threat indicator based on its functional or operational role. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT categoryField;

  /** Analyst commentary or source commentary about an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "comment")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT commentField;

  /**
   * The confidence of an indicator being malicious and/or pertinent, normalized to the caption of
   * the confidence_id value. In the case of 'Other', it is defined by the event source or analyst.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT confidenceField;

  /**
   * The normalized confidence refers to the accuracy of collected information related to the OSINT
   * or how pertinent an indicator or analysis is to a specific event or finding. A low confidence
   * means that the information collected or analysis conducted lacked detail or is not accurate
   * enough to qualify an indicator as fully malicious.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT confidenceIdField;

  /** The timestamp when the indicator was initially created or identified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The timestamp when the indicator was initially created or identified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The identifier of the user, system, or organization that contributed the indicator. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "creator")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser creatorField;

  /** A detailed explanation of the indicator, including its context, purpose, and relevance. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /** The specific detection pattern or signature associated with the indicator. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_pattern")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT detectionPatternField;

  /**
   * The detection pattern type, normalized to the caption of the detection_pattern_type_id value.
   * In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_pattern_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT detectionPatternTypeField;

  /** Specifies the type of detection pattern used to identify the associated threat indicator. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_pattern_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT detectionPatternTypeIdField;

  /** Any email authentication information pertinent to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_auth")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectEmailAuth emailAuthField;

  /** Any email information pertinent to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "email")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectEmail emailField;

  /** The expiration date of the indicator, after which it is no longer considered reliable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  /** The expiration date of the indicator, after which it is no longer considered reliable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  /** A unique identifier assigned by an external system for cross-referencing. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "external_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT externalUidField;

  /** Any pertinent file information related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  /**
   * A grouping of adversarial behaviors and resources believed to be associated with specific
   * threat actors or campaigns. Intrusion sets often encompass multiple campaigns and are used to
   * organize related activities under a common label.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "intrusion_sets")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      intrusionSetsField;

  /** Lockheed Martin Kill Chain Phases pertinent to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kill_chain")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKillChainPhase>
      killChainField;

  /** Tags or keywords associated with the indicator to enhance searchability. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> labelsField;

  /** Any pertinent geolocation information related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation locationField;

  /** A list of Malware objects, describing details about the identified malware. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectMalware> malwareField;

  /** The timestamp of the last modification or update to the indicator. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The timestamp of the last modification or update to the indicator. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /**
   * The <code>name</code> is a pointer/reference to an attribute within the OCSF event data. For
   * example: file.name.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * Provides a reference to an external source of information related to the CTI being represented.
   * This may include a URL, a document, or some other type of reference that provides additional
   * context or information about the CTI.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "references")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> referencesField;

  /** Any analytics related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_analytics")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalytic>
      relatedAnalyticsField;

  /**
   * Related reputational analysis from third-party engines and analysts for a given indicator or
   * OSINT analysis.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reputation")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectReputation reputationField;

  /** A numerical representation of the threat indicator’s risk level. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskScoreField;

  /** Any pertinent script information related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "script")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectScript scriptField;

  /**
   * Represents the severity level of the threat indicator, typically reflecting its potential
   * impact or damage.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT severityField;

  /**
   * The normalized severity level of the threat indicator, typically reflecting its potential
   * impact or damage.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT severityIdField;

  /** Any digital signatures or hashes related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "signatures")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDigitalSignature>
      signaturesField;

  /**
   * The source URL of an indicator or OSINT analysis, e.g., a URL back to a TIP, report, or
   * otherwise.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /**
   * Any pertinent subdomain information - such as those generated by a Domain Generation Algorithm
   * - related to an indicator or OSINT analysis.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subdomains")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> subdomainsField;

  /** A CIDR or network block related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeSubnetT subnetField;

  /**
   * A threat actor is an individual or group that conducts malicious cyber activities, often with
   * financial, political, or ideological motives.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "threat_actor")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectThreatActor threatActorField;

  /**
   * The <a target='_blank' href='https://www.first.org/tlp/'>Traffic Light Protocol</a> was created
   * to facilitate greater sharing of potentially sensitive information and more effective
   * collaboration. TLP provides a simple and intuitive schema for indicating with whom potentially
   * sensitive information can be shared.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tlp")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT tlpField;

  /** The OSINT indicator type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The OSINT indicator type ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The unique identifier for the OSINT object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The timestamp indicating when the associated indicator or intelligence was added to the system
   * or repository.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uploaded_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT uploadedTimeDtField;

  /**
   * The timestamp indicating when the associated indicator or intelligence was added to the system
   * or repository.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uploaded_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT uploadedTimeField;

  /** The actual indicator value in scope, e.g., a SHA-256 hash hexdigest or a domain name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT valueField;

  /** The vendor name of a tool which generates intelligence or provides indicators. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vendorNameField;

  /** Any vulnerabilities related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vulnerabilities")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectVulnerability>
      vulnerabilitiesField;

  /** Any pertinent WHOIS information related to an indicator or OSINT analysis. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "whois")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectWhois whoisField;
}
