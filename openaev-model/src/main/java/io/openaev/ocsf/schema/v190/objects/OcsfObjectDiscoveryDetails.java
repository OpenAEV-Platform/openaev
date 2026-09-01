package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDiscoveryDetails extends OcsfObject {
  /** The number of discovered entities of the specified type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  /**
   * Details about where in the target entity, specified information was discovered. Only the
   * attributes, relevant to the target entity type should be populated.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "occurrence_details")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOccurrenceDetails occurrenceDetailsField;

  /**
   * Details about where in the target entity, specified information was discovered. Only the
   * attributes, relevant to the target entity type should be populated.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "occurrences")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectOccurrenceDetails>
      occurrencesField;

  /**
   * The rule associated with this discovery, usually part of the higher level <code>policy</code>
   * from an enclosing class or object.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rule")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRule ruleField;

  /**
   * The specific type of information that was discovered. e.g.<code> name, phone_number, etc.
   * </code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** Optionally, the specific value of discovered information. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;
}
