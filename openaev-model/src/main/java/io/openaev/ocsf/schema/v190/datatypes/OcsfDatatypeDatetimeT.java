package io.openaev.ocsf.schema.v190.datatypes;

import io.openaev.ocsf.schema.BaseType;

public class OcsfDatatypeDatetimeT extends BaseType<java.lang.String> {

  @java.lang.Override
  protected boolean validate() {
    return getValue()
        .matches(
            "^\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?([Zz]|[\\+-]\\d{2}:\\d{2})?$");
  }

  public OcsfDatatypeDatetimeT(java.lang.String value) {
    super(value);
  }
}
