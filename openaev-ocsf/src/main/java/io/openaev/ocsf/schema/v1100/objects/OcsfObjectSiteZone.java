package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSiteZone extends OcsfObject {
  /**
   * Indicates whether the intrusion detection or alarm system protecting the site zone was armed
   * (active) at the time of the event. <code>false</code> means it was disarmed.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_armed")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeBooleanT isArmedField;

  /** The name of the entity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The site zone type, normalized to the caption of the <code>type_id</code> value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The normalized classification of the site zone by security posture. These are generic tiers by
   * security posture. Regime-specific tiers map onto them rather than extending the list: for
   * example, a controlled, limited, or protected area maps to Restricted, and an exclusion, vital,
   * or material access area maps to Critical. Carry the native tier name in <code>type</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The unique identifier of the entity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT uidNumericField;
}
