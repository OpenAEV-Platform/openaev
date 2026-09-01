package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectTactic extends OcsfObject {
  /**
   * The Tactic name that is associated with the attack technique. For example: <code>Reconnaissance
   * </code> or <code>ML Model Access</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The versioned permalink of the Tactic. For example: <code>
   * https://attack.mitre.org/versions/v14/tactics/TA0043/</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /**
   * The Tactic ID that is associated with the attack technique. For example: <code>TA0043</code>,
   * or <code>AML.TA0000</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
