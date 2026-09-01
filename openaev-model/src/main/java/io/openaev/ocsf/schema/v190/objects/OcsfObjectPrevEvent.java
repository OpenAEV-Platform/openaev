package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPrevEvent extends OcsfObject {
  /**
   * The fingerprint of the previous event, binding this reference to the previous event's content
   * so that altering or substituting the previous event breaks the link. Refer to specific usage.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint fingerprintField;

  /**
   * The event type identifier of the previous event, as carried in that event's <code>type_uid
   * </code>. It directs a consumer to the event class, and therefore the table or store, where the
   * previous event resides.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT typeUidField;

  /**
   * The unique identifier of the previous event, as carried in that event's <code>metadata.uid
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
