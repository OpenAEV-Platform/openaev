package io.openaev.ocsf.schema.v190.datatypes;

import io.openaev.ocsf.schema.BaseType;

public class OcsfDatatypeBytestringT extends BaseType<java.lang.String> {

  public OcsfDatatypeBytestringT(java.lang.String value) {
    super(value);
  }

  @java.lang.Override
  protected boolean validate() {
    return getValue()
        .matches(
            "^(?:(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)|(?:[A-Za-z0-9_-]{4})*(?:[A-Za-z0-9_-]{2}==|[A-Za-z0-9_-]{3}=))?$");
  }
}
