package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSubTechnique extends OcsfObject {
  /**
   * The name of the attack sub-technique. For example: <code>Scanning IP Blocks</code> or <code>
   * User Execution: Unsafe ML Artifacts</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The versioned permalink of the attack sub-technique. For example: <code>
   * https://attack.mitre.org/versions/v14/techniques/T1595/001/</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /**
   * The unique identifier of the attack sub-technique. For example: <code>T1595.001</code> or
   * <code>AML.T0011.000</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
