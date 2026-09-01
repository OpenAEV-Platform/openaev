package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCisControl extends OcsfObject {
  /**
   * The CIS Control description. For example: <i>Uninstall or disable unnecessary services on
   * enterprise assets and software, such as an unused file sharing service, web application module,
   * or service function.</i>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /**
   * The CIS Control name. For example: <i>4.8 Uninstall or Disable Unnecessary Services on
   * Enterprise Assets and Software.</i>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The CIS Control version. For example: <i>v8</i>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
