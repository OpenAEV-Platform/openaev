package io.openaev.ocsf.schema.v190.datatypes;

import io.openaev.ocsf.schema.BaseType;

public class OcsfDatatypeMacT extends BaseType<java.lang.String> {

  @java.lang.Override
  protected boolean validate() {
    return getValue().matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
  }

  public OcsfDatatypeMacT(java.lang.String value) {
    super(value);
  }
}
