package io.openbas.stix.types;

public class Identifier extends BaseType<java.lang.String> {
  public Identifier(String type, String id) {
    super("%s--%s".formatted(type, id));
  }

  public Identifier(String value) {
    super(value);
  }
}
