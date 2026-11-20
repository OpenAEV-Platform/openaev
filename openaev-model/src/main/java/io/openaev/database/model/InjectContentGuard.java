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
import java.util.Set;

/** Refuses an inject whose single-choice field holds a list: its execution cannot read it. */
public class InjectContentGuard {

  private static final Set<String> CHOICE_TYPES = Set.of("select", "choice", "dependency-select");
  private static final String SINGLE = "1";

  @PrePersist
  @PreUpdate
  void refuseListInSingleChoice(Inject inject) {
    ObjectNode content = inject.getContent();
    ObjectNode contract =
        inject.getInjectorContract().map(InjectorContract::getConvertedContent).orElse(null);
    if (content == null || contract == null) {
      return;
    }
    for (JsonNode field : contract.path(CONTRACT_CONTENT_FIELDS)) {
      String key = field.path(CONTRACT_ELEMENT_CONTENT_KEY).asText();
      if (CHOICE_TYPES.contains(field.path(CONTRACT_ELEMENT_CONTENT_TYPE).asText())
          && SINGLE.equals(field.path(CONTRACT_ELEMENT_CONTENT_CARDINALITY).asText())
          && content.path(key).isArray()) {
        throw InjectContentRefusedException.singleValueExpected(key);
      }
    }
  }
}
