package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectOsint {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "signatures")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDigitalSignature signaturesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidenceIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_auth")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEmailAuth emailAuthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "kill_chain")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKillChainPhase killChainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeSubnetT subnetField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT severityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "creator")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser creatorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT confidenceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "threat_actor")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectThreatActor threatActorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "script")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectScript scriptField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile fileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT severityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "references")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT referencesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEmail emailField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vulnerabilities")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectVulnerability vulnerabilitiesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack attacksField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT labelsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "external_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT externalUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tlp")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT tlpField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_analytics")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalytic relatedAnalyticsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation locationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "campaign")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCampaign campaignField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_pattern_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT detectionPatternTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "whois")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectWhois whoisField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uploaded_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT uploadedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uploaded_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT uploadedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_pattern_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT detectionPatternTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "intrusion_sets")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT intrusionSetsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "comment")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT commentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subdomains")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT subdomainsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "autonomous_system")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAutonomousSystem autonomousSystemField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reputation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectReputation reputationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_pattern")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT detectionPatternField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "answers")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsAnswer answersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMalware malwareField;
}
