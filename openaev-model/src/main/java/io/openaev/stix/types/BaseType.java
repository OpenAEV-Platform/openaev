package io.openaev.stix.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.stix.parsing.StixSerialisable;
import java.util.Objects;
import lombok.Getter;

@Getter
public abstract class BaseType<T> implements StixSerialisable {
  private final T value;

  public BaseType(T value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (o.getClass() == this.getClass()) {
      BaseType<?> that = (BaseType<?>) o;
      return this.value.equals(that.value);
    }
    return Objects.equals(value, o);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public JsonNode toStix(ObjectMapper mapper) {
    return mapper.valueToTree(this.value);
  }
}
