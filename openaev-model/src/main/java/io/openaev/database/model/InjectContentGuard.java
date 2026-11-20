package io.openaev.database.model;

import static io.openaev.database.model.InjectorContract.CONTRACT_CONTENT_FIELDS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_CARDINALITY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.exception.InjectContentRefusedException;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks each inject content field against the shape its contract field expects: what the execution
 * cannot read is refused, any other mismatch is only logged until the writers producing it are
 * known.
 */
@Slf4j
public class InjectContentGuard {

  private static final String SINGLE = "1";

  @PrePersist
  @PreUpdate
  void checkContentShape(Inject inject) {
    ObjectNode content = inject.getContent();
    ObjectNode contract =
        inject.getInjectorContract().map(InjectorContract::getConvertedContent).orElse(null);
    if (content == null || contract == null) {
      return;
    }
    for (JsonNode field : contract.path(CONTRACT_CONTENT_FIELDS)) {
      String key = field.path(CONTRACT_ELEMENT_CONTENT_KEY).asText();
      JsonNode value = content.get(key);
      if (value == null || value.isNull()) {
        continue;
      }
      boolean single = SINGLE.equals(field.path(CONTRACT_ELEMENT_CONTENT_CARDINALITY).asText());
      String type = field.path(CONTRACT_ELEMENT_CONTENT_TYPE).asText();
      ContractFieldShape.of(type)
          .ifPresent(shape -> check(shape, inject, key, type, value, single));
    }
  }

  private static void check(
      ContractFieldShape shape,
      Inject inject,
      String key,
      String type,
      JsonNode value,
      boolean single) {
    if (shape.refuses(value, single)) {
      throw InjectContentRefusedException.singleValueExpected(key);
    }
    if (!shape.accepts(value, single)) {
      log.warn(
          "Inject {} of contract {}: field '{}' ({}) holds an unexpected value",
          inject.getId(),
          inject.getInjectorContract().map(InjectorContract::getId).orElse(null),
          key,
          type);
    }
  }
}
