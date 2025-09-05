package io.openbas.stix.objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.stix.parsing.ParsingException;
import io.openbas.stix.parsing.StixSerialisable;
import io.openbas.stix.types.BaseType;
import io.openbas.stix.types.StixString;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

public class ObjectBase implements StixSerialisable {
  @Getter @Setter private Map<String, BaseType<?>> properties;

  public BaseType<?> getProperty(String name) {
    return properties.get(name);
  }

  public boolean hasProperty(String name) {
    return properties.containsKey(name);
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

  public void setIfListPresent(String propName, Consumer<List<String>> setter) {
    if (this.hasProperty(propName) && this.getProperty(propName).getValue() != null) {
      Object value = getProperty(propName).getValue();
      if (value instanceof List<?>) {
        List<String> strings =
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
                    .collect(Collectors.toList());

        setter.accept(strings);
      }
    }
  }

  public void setInstantIfPresent(String propName, Consumer<Instant> setter) {
    if (this.hasProperty(propName) && this.getProperty(propName).getValue() != null) {
      setter.accept(Instant.parse(this.getProperty(propName).getValue().toString()));
    }
  }
}
