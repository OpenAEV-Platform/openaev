package io.openaev.ocsf.schema.v190.datatypes;

import io.openaev.ocsf.schema.BaseType;

public class OcsfDatatypeUuidT extends BaseType<java.lang.String> {

  @java.lang.Override
  protected boolean validate() {
    return getValue()
        .matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  }

  public OcsfDatatypeUuidT(java.lang.String value) {
    super(value);
  }
}
