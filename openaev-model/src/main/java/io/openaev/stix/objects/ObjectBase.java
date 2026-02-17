package io.openaev.stix.objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.objects.constants.ExtendedProperties;
import io.openaev.stix.parsing.ParsingException;
import io.openaev.stix.parsing.StixSerialisable;
import io.openaev.stix.types.BaseType;
import io.openaev.stix.types.Dictionary;
import io.openaev.stix.types.Identifier;
import io.openaev.stix.types.StixString;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ObjectBase implements StixSerialisable {
  private final Map<String, BaseType<?>> properties;

  protected ObjectBase(Map<String, BaseType<?>> properties) {
    this.properties = properties;
  }

  public Identifier getId() {
    return (Identifier) this.getProperty(CommonProperties.ID);
  }

  public StixString getType() {
    return (StixString) this.getProperty(CommonProperties.TYPE);
  }

  public BaseType<?> getProperty(String name) {
    return properties.get(name);
  }

  public BaseType<?> getProperty(CommonProperties property) {
    return this.getProperty(property.toString());
  }

  public void setProperty(String name, BaseType<?> value) {
    properties.put(name, value);
  }

  public boolean hasProperty(String name) {
    return properties.containsKey(name);
  }

  public boolean hasProperty(CommonProperties propertySpec) {
    return this.hasProperty(propertySpec.toString());
  }

  public boolean hasExtension(String id) {
    return hasProperty(CommonProperties.EXTENSIONS)
        && ((Dictionary) getProperty(CommonProperties.EXTENSIONS)).has(id);
  }

  public boolean hasExtension(ExtendedProperties extendedPropertySpec) {
    return hasExtension(extendedPropertySpec.toString());
  }

  public BaseType<?> getExtension(String id) {
    return ((Dictionary) getProperty(CommonProperties.EXTENSIONS)).get(id);
  }

  public BaseType<?> getExtension(ExtendedProperties extendedPropertySpec) {
    return getExtension(extendedPropertySpec.toString());
  }

  public List<Dictionary> getExtensionObservables(ExtendedProperties extendedPropertySpec) {
    Dictionary extension = (Dictionary) getExtension(extendedPropertySpec);
    if (extension == null) {
      return new ArrayList<>();
    }

    io.openaev.stix.types.List<Dictionary> stixObservables =
        (io.openaev.stix.types.List<Dictionary>)
            extension.getValue().get(CommonProperties.OBSERVABLE_VALUES.toString());
    if (stixObservables != null) {
      return stixObservables.getValue();
    }
    return new ArrayList<>();
  }

  @Override
  public JsonNode toStix(ObjectMapper mapper) {
    ObjectNode node = mapper.createObjectNode();
    for (Map.Entry<String, BaseType<?>> entry : properties.entrySet()) {
      node.set(entry.getKey(), entry.getValue().toStix(mapper));
    }
    return node;
  }

  public String getRequiredProperty(String propName) throws ParsingException {
    if (!this.hasProperty(propName) || this.getProperty(propName).getValue() == null) {
      throw new ParsingException("Missing required property: " + propName);
    }
    return this.getProperty(propName).getValue().toString();
  }

  public String getOptionalProperty(String propName, String defaultValue) {
    if (this.hasProperty(propName) && this.getProperty(propName).getValue() != null) {
      return this.getProperty(propName).getValue().toString();
    }
    return defaultValue;
  }

  public void setIfPresent(String propName, Consumer<String> setter) {
    if (this.hasProperty(propName) && this.getProperty(propName).getValue() != null) {
      setter.accept(this.getProperty(propName).getValue().toString());
    }
  }

  public void setIfSetPresent(String propName, Consumer<Set<String>> setter) {
    if (this.hasProperty(propName) && this.getProperty(propName).getValue() != null) {
      Object value = getProperty(propName).getValue();
      if (value instanceof List<?>) {
        Set<String> strings =
            ((List<?>) value)
                .stream()
                    .map(
                        v -> {
                          if (v instanceof StixString) {
                            return ((StixString) v).getValue();
                          } else {
                            return v.toString();
                          }
                        })
                    .collect(Collectors.toSet());

        setter.accept(strings);
      }
    }
  }

  public void setInstantIfPresent(CommonProperties propName, Consumer<Instant> setter) {
    if (this.hasProperty(propName) && this.getProperty(propName).getValue() != null) {
      setter.accept(Instant.parse(this.getProperty(propName).getValue().toString()));
    }
  }
}
