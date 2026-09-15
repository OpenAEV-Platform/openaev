package io.openaev.ocsf.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parsing.OcsfSerialisable;
import java.util.Objects;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class OcsfDatatype<T> implements OcsfSerialisable {
  @Getter @JsonValue private final T value;

  public OcsfDatatype(T value) {
    this.value = value;
  }

  /**
   * Introspect and validate the format of the underlying data fragment. Some OCSF data types have
   * validation rules (regexp based).
   *
   * @return true if the underlying data fragment clears the validation rules
   */
  public boolean validate() {
    return true; // default to always valid
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || o.getClass() != this.getClass()) {
      return false;
    }
    OcsfDatatype<?> that = (OcsfDatatype<?>) o;
    return Objects.equals(this.value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return this.value.toString();
  }

  @Override
  public JsonNode toOcsf(ObjectMapper mapper) {
    return mapper.valueToTree(this.value);
  }
}
