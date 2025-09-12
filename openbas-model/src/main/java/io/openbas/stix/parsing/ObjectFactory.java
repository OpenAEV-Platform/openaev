package io.openbas.stix.parsing;

import io.openbas.stix.objects.DomainObject;
import io.openbas.stix.objects.ObjectBase;
import io.openbas.stix.objects.RelationshipObject;
import io.openbas.stix.objects.constants.CommonProperties;
import io.openbas.stix.objects.constants.ObjectTypes;
import io.openbas.stix.types.BaseType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ObjectFactory {
  private static final Map<ObjectTypes, Function<Map<String, BaseType<?>>, ObjectBase>>
      constructors = new HashMap<>();

  static {
    constructors.put(ObjectTypes.RELATIONSHIP, RelationshipObject::new);
    constructors.put(ObjectTypes.SIGHTING, RelationshipObject::new);
    constructors.put(ObjectTypes.ATTACK_PATTERN, DomainObject::new);
    constructors.put(ObjectTypes.VULNERABILITY, DomainObject::new);
    constructors.put(ObjectTypes.IDENTITY, DomainObject::new);
    constructors.put(ObjectTypes.DEFAULT, DomainObject::new);
  }

  public static ObjectBase instantiateFromProps(Map<String, BaseType<?>> props)
      throws ParsingException {
    if (!props.containsKey(CommonProperties.TYPE.toString())) {
      throw new ParsingException("Node found without type property");
    }
    String type = (String) props.get(CommonProperties.TYPE.toString()).getValue();
    Function<Map<String, BaseType<?>>, ObjectBase> ctor =
        constructors.get(ObjectTypes.fromString(type));
    return ctor.apply(props);
  }
}
