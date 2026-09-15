package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectMitigation extends OcsfObject {
  /**
   * The D3FEND countermeasures that are associated with the attack technique. For example: ATT&CK
   * Technique <code>T1003</code> is addressed by Mitigation <code>M1027</code>, and D3FEND
   * Technique <code>D3-OTP</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "countermeasures")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fend> countermeasuresField;

  /**
   * The Mitigation name that is associated with the attack technique. For example: <code>
   * Password Policies</code>, or <code>Code Signing</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The versioned permalink of the Mitigation. For example: <code>
   * https://attack.mitre.org/versions/v14/mitigations/M1027</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  /**
   * The Mitigation ID that is associated with the attack technique. For example: <code>M1027</code>
   * , or <code>AML.M0013</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
