package io.openaev.ocsf.schema.v190.datatypes;

import io.openaev.ocsf.schema.OcsfDatatype;

public class OcsfDatatypeDatetimeT extends OcsfDatatype<java.lang.String> {

  public OcsfDatatypeDatetimeT(java.lang.String value) {
    super(value);
  }

  @java.lang.Override
  protected boolean validate() {
    return getValue()
        .matches(
            "^\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?([Zz]|[\\+-]\\d{2}:\\d{2})?$");
  }
}
