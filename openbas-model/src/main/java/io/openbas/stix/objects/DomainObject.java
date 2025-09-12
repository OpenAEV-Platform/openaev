package io.openbas.stix.objects;

import io.openbas.stix.types.BaseType;
import java.util.Map;

public class DomainObject extends ObjectBase {
  public DomainObject(Map<String, BaseType<?>> properties) {
    super(properties);
  }
}
