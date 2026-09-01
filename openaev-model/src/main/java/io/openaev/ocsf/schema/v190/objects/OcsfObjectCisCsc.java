package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCisCsc extends OcsfObject {
  /**
   * A Control is prescriptive, prioritized, and simplified set of best practices that one can use
   * to strengthen their cybersecurity posture. e.g. AWS SecurityHub Controls, CIS Controls.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "control")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT controlField;

  /** The CIS critical security control version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
