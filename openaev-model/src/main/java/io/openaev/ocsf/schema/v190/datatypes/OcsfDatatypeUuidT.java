package io.openaev.ocsf.schema.v190.datatypes;

import io.openaev.ocsf.schema.OcsfDatatype;

public class OcsfDatatypeUuidT extends OcsfDatatype<java.lang.String> {

  public OcsfDatatypeUuidT(java.lang.String value) {
    super(value);
  }

  @java.lang.Override
  protected boolean validate() {
    return getValue()
        .matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  }
}
