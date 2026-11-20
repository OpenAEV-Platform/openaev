package io.openaev.database.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/** The values an inject content field may hold, by the type of its contract field. */
enum ContractFieldShape {
  TEXT("text", "textarea", "document") {
    @Override
    boolean accepts(JsonNode value, boolean single) {
      return value.isTextual();
    }
  },
  NUMBER("number") {
    @Override
    boolean accepts(JsonNode value, boolean single) {
      return value.isNumber();
    }
  },
  CHECKBOX("checkbox") {
    @Override
    boolean accepts(JsonNode value, boolean single) {
      return value.isBoolean();
    }
  },
  PICKED("select", "choice", "dependency-select", "targeted-asset") {
    @Override
    boolean accepts(JsonNode value, boolean single) {
      return single ? value.isTextual() : isListOf(value, JsonNode::isTextual);
    }

    @Override
    boolean refuses(JsonNode value, boolean single) {
      return single && value.isArray();
    }
  },
  EXPECTATIONS("expectation") {
    @Override
    boolean accepts(JsonNode value, boolean single) {
      return isListOf(value, JsonNode::isObject);
    }
  };

  private final Set<String> types;

  ContractFieldShape(String... types) {
    this.types = Set.of(types);
  }

  abstract boolean accepts(JsonNode value, boolean single);

  /** True when the execution cannot read the value at all, so saving it is refused. */
  boolean refuses(JsonNode value, boolean single) {
    return false;
  }

  static Optional<ContractFieldShape> of(String type) {
    return Arrays.stream(values()).filter(shape -> shape.types.contains(type)).findFirst();
  }

  private static boolean isListOf(JsonNode value, Predicate<JsonNode> item) {
    return value.isArray() && StreamSupport.stream(value.spliterator(), false).allMatch(item);
  }
}
