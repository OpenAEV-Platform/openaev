package io.openaev.service.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What differs between two versions of a payload, field by field, each with its value before and
 * after. The fields are those of the content hash ({@link PayloadContentHashService}), so every
 * change that makes a new version (RFC 0005 §4.1) shows here, and nothing else does.
 */
@Service
@RequiredArgsConstructor
public class PayloadFieldChangeService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final PayloadContentHashService payloadContentHashService;
  private final InjectorContractRepository injectorContractRepository;

  /** Between two saved versions, each with the attack patterns of its own action. */
  public ArrayNode between(@Nullable final Payload before, @NotNull final Payload after) {
    return between(before, attackPatternsOf(before), after, attackPatternsOf(after));
  }

  /**
   * Between two contents whose attack patterns the caller knows: a version not saved yet, or one
   * whose action was just changed. {@code before} null: everything {@code after} has is added.
   */
  public ArrayNode between(
      @Nullable final Payload before,
      @Nullable final Collection<AttackPattern> beforePatterns,
      @NotNull final Payload after,
      @Nullable final Collection<AttackPattern> afterPatterns) {
    ObjectNode beforeShown =
        before == null
            ? MAPPER.createObjectNode()
            : payloadContentHashService.displayTree(before, beforePatterns);
    ObjectNode afterShown = payloadContentHashService.displayTree(after, afterPatterns);
    ObjectNode beforeCompared;
    ObjectNode afterCompared;
    try {
      beforeCompared =
          before == null
              ? MAPPER.createObjectNode()
              : payloadContentHashService.canonicalTree(before, beforePatterns);
      afterCompared = payloadContentHashService.canonicalTree(after, afterPatterns);
    } catch (RuntimeException e) {
      // An attached file cannot be read: compare what people see (file names).
      beforeCompared = beforeShown;
      afterCompared = afterShown;
    }
    Set<String> fields = new LinkedHashSet<>();
    afterShown.fieldNames().forEachRemaining(fields::add);
    beforeShown.fieldNames().forEachRemaining(fields::add);
    ArrayNode changes = MAPPER.createArrayNode();
    for (String field : fields) {
      if (!Objects.equals(beforeCompared.get(field), afterCompared.get(field))) {
        ObjectNode change = changes.addObject();
        change.put("field", field);
        change.set("before", valueOrNull(beforeShown.get(field)));
        change.set("after", valueOrNull(afterShown.get(field)));
      }
    }
    return changes;
  }

  /** The attack patterns of the action running this version; none for a version not saved yet. */
  public List<AttackPattern> attackPatternsOf(@Nullable final Payload payload) {
    if (payload == null || payload.getId() == null) {
      return List.of();
    }
    return injectorContractRepository
        .findInjectorContractByPayload(payload)
        .map(InjectorContract::getAttackPatterns)
        .<List<AttackPattern>>map(ArrayList::new)
        .orElseGet(List::of);
  }

  private static JsonNode valueOrNull(JsonNode value) {
    return value == null ? MAPPER.nullNode() : value;
  }
}
