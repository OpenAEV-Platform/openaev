package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectObservable extends OcsfObject {
  /**
   * The unique identifier (<code>metadata.uid</code>) of the source OCSF event from which this
   * observable was extracted. This field enables linking observables back to their originating
   * event data when observables are stored in a separate location or system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "event_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT eventUidField;

  /**
   * The full name of the observable attribute. The <code>name</code> is a pointer/reference to an
   * attribute within the OCSF event data. For example: <code>file.name</code>. Array attributes may
   * be represented in one of three ways. For example: <code>resources.uid</code>, <code>
   * resources[].uid</code>, <code>resources[0].uid</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** Contains the original and normalized reputation scores. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reputation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectReputation reputationField;

  /** The observable value type name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The observable value type identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The OCSF event type UID (<code>type_uid</code>) of the source event that this observable was
   * extracted from. This field enables filtering and categorizing observables by their originating
   * event type. For example: <code>300101</code> for Network Activity (class_uid 3001) with
   * activity_id 1.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT typeUidField;

  /**
   * The value associated with the observable attribute. The meaning of the value depends on the
   * observable type.<br>
   * If the <code>name</code> refers to a scalar attribute, then the <code>value</code> is the value
   * of the attribute.<br>
   * If the <code>name</code> refers to an object attribute, then the <code>value</code> is not
   * populated.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;
}
