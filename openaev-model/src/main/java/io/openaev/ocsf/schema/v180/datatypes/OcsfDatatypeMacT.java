package io.openaev.ocsf.schema.v180.datatypes;

import io.openaev.ocsf.schema.OcsfDatatype;

public class OcsfDatatypeMacT extends OcsfDatatype<java.lang.String> {

  public OcsfDatatypeMacT(java.lang.String value) {
    super(value);
  }

  @java.lang.Override
  protected boolean validate() {
    return getValue().matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
  }
}
