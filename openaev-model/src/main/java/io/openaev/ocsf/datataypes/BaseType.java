package io.openaev.ocsf.datataypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parsing.OcsfSerialisable;
import java.util.Objects;
import lombok.Getter;

@Getter
public abstract class BaseType<T> implements OcsfSerialisable {
  private final T value;

  public BaseType(T value) {
    this.value = value;
  }

  /**
   * Introspect and validate the format of the underlying data fragment. Some OCSF data types have
   * validation rules (regexp based).
   *
   * @return true if the underlying data fragment clears the validation rules
   */
  protected boolean validate() {
    return true; // default to always valid
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
  public JsonNode toOcsf(ObjectMapper mapper) {
    return mapper.valueToTree(this.value);
  }
}
