package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFirewallRule extends OcsfObject {
  /** The rule category. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryField;

  /** The rule trigger condition for the rule. For example: SQL_INJECTION. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "condition")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT conditionField;

  /** The description of the rule that generated the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  /** The rule response time duration, usually used for challenge completion time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT durationField;

  /** The data in a request that rule matched. For example: '["10","and","1"]'. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "match_details")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      matchDetailsField;

  /**
   * The location of the matched data in the source which resulted in the triggered firewall rule.
   * For example: HEADER.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "match_location")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT matchLocationField;

  /** The name of the rule that generated the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The rate limit for a rate-based rule. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rate_limit")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT rateLimitField;

  /** The sensitivity of the firewall rule in the matched event. For example: HIGH. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sensitivity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sensitivityField;

  /** The rule type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The unique identifier of the rule that generated the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /** The rule version. For example: <code>1.1</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
