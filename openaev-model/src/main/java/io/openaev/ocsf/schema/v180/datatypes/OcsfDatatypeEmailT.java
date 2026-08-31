package io.openaev.ocsf.schema.v180.datatypes;

import io.openaev.ocsf.schema.OcsfDatatype;

public class OcsfDatatypeEmailT extends OcsfDatatype<java.lang.String> {

  public OcsfDatatypeEmailT(java.lang.String value) {
    super(value);
  }

  @java.lang.Override
  protected boolean validate() {
    return getValue().matches("^[a-zA-Z0-9!#$%&'*+-/=?^_`{|}~.]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+$");
  }
}
