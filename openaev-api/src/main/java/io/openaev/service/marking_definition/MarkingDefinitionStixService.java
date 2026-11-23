package io.openaev.service.marking_definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.MarkingDefinitionStixIds;
import io.openaev.database.repository.MarkingDefinitionRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marking definitions in OpenCTI's STIX 2.1 format, so a marking crosses the platform with the same
 * id: the export writes {@code marking-definition} objects carrying the stored STIX id and
 * OpenCTI's order and color extension; the import keeps the id OpenCTI gave each marking.
 *
 * <p>An import never overwrites: a marking whose STIX id the tenant already has is reported
 * existing, one whose type and definition a local definition already uses under another STIX id is
 * reported a conflict, and only the others are created, under the caller's clearance as through
 * {@link MarkingDefinitionService#create}.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MarkingDefinitionStixService {

  /** OpenCTI's extension id (opencti-graphql src/types/stix-2-1-extensions.ts). */
  public static final String OPENCTI_EXTENSION =
      "extension-definition--ea279b3e-5c71-4632-ac08-831c66a786ba";

  static final String DEFAULT_COLOR = "#ffffff";
  private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
  private static final JsonNode EMPTY = new ObjectMapper().createObjectNode();

  private final MarkingDefinitionRepository repository;
  private final MarkingDefinitionService markingDefinitionService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** What an import did with each marking it was given. */
  public enum Outcome {
    CREATED,
    EXISTING,
    CONFLICT,
    INVALID
  }

  /** One line of an import report. */
  public record ImportLine(String stixId, String type, String definition, Outcome outcome) {}

  // -- EXPORT --

  /**
   * The marking definitions of the tenants in scope the caller is cleared for, as a STIX 2.1
   * bundle: a definition above the caller's clearance is not revealed, as by {@link
   * MarkingDefinitionService#listAssignable}.
   *
   * @param ctx transaction context containing tenant scope
   * @return a {@code bundle} of {@code marking-definition} objects
   */
  @Transactional(readOnly = true)
  public ObjectNode exportBundle(@NotNull TxCtx ctx) {
    List<MarkingDefinition> definitions = markingDefinitionService.listAssignable(ctx);
    ObjectNode bundle = objectMapper.createObjectNode();
    bundle.put("type", "bundle");
    bundle.put("id", "bundle--" + UUID.randomUUID());
    ArrayNode objects = bundle.putArray("objects");
    definitions.forEach(definition -> objects.add(toStix(definition)));
    return bundle;
  }

  /**
   * One marking definition as OpenCTI writes it.
   *
   * @param definition the stored definition
   * @return its STIX 2.1 {@code marking-definition} object
   */
  public ObjectNode toStix(MarkingDefinition definition) {
    ObjectNode stix = objectMapper.createObjectNode();
    stix.put("type", "marking-definition");
    stix.put("spec_version", "2.1");
    stix.put(
        "id",
        definition.getStixId() != null
            ? definition.getStixId()
            : MarkingDefinitionStixIds.generate(definition.getType(), definition.getDefinition()));
    Instant created = definition.getCreatedAt();
    if (created != null) {
      stix.put("created", created.toString());
    }
    stix.put("definition_type", definition.getType());
    stix.put("name", definition.getDefinition());
    stix.put("x_opencti_order", definition.getOrder());
    stix.put("x_opencti_color", definition.getColor());
    ObjectNode extension = stix.putObject("extensions").putObject(OPENCTI_EXTENSION);
    extension.put("extension_type", "property-extension");
    extension.put("order", definition.getOrder());
    extension.put("color", definition.getColor());
    return stix;
  }

  // -- IMPORT --

  /**
   * Imports the {@code marking-definition} objects of a STIX bundle (or of a bare object or array)
   * into a tenant, keeping the STIX id OpenCTI gave each of them. Other object types are ignored.
   *
   * @param stix a STIX 2.1 bundle, a list of objects or one object
   * @param tenantId tenant that receives the new definitions
   * @param userId the current user, whose clearance bounds the orders
   * @return one line per marking-definition object, in input order
   */
  public List<ImportLine> importStix(
      @NotNull JsonNode stix, @NotBlank String tenantId, @NotBlank String userId) {
    List<ImportLine> report = new ArrayList<>();
    for (JsonNode object : markingObjects(stix)) {
      report.add(importOne(object, tenantId, userId));
    }
    return report;
  }

  private ImportLine importOne(JsonNode object, String tenantId, String userId) {
    String stixId = text(object, "id");
    String type = upper(text(object, "definition_type"));
    String definition = upper(definitionOf(object, type));
    if (!MarkingDefinitionStixIds.isValid(stixId) || type == null || definition == null) {
      return new ImportLine(stixId, type, definition, Outcome.INVALID);
    }
    if (repository.findByStixIdAndTenantId(stixId, tenantId).isPresent()) {
      return new ImportLine(stixId, type, definition, Outcome.EXISTING);
    }
    Optional<MarkingDefinition> sameName =
        repository.findByTypeAndDefinitionAndTenantId(type, definition, tenantId);
    if (sameName.isPresent()) {
      return new ImportLine(stixId, type, definition, Outcome.CONFLICT);
    }
    MarkingDefinitionInput input =
        new MarkingDefinitionInput(type, definition, colorOf(object), orderOf(object));
    MarkingDefinition created = markingDefinitionService.create(input, tenantId, userId, stixId);
    return new ImportLine(created.getStixId(), type, definition, Outcome.CREATED);
  }

  private static List<JsonNode> markingObjects(JsonNode stix) {
    List<JsonNode> candidates = new ArrayList<>();
    if (stix.isArray()) {
      stix.forEach(candidates::add);
    } else if (stix.has("objects") && stix.get("objects").isArray()) {
      stix.get("objects").forEach(candidates::add);
    } else {
      candidates.add(stix);
    }
    return candidates.stream()
        .filter(node -> "marking-definition".equals(text(node, "type")))
        .toList();
  }

  /**
   * The name OpenCTI writes, else the STIX 2.0 {@code definition} object ({@code {tlp: amber}}).
   */
  private static String definitionOf(JsonNode object, String type) {
    String name = text(object, "name");
    if (name != null) {
      return name;
    }
    JsonNode legacy = object.get("definition");
    if (legacy == null || !legacy.isObject() || type == null) {
      return null;
    }
    String value = text(legacy, type.toLowerCase(Locale.ROOT));
    if (value == null) {
      value = text(legacy, "statement");
    }
    if (value == null) {
      return null;
    }
    return "TLP".equals(type) && !value.toUpperCase(Locale.ROOT).startsWith("TLP:")
        ? "TLP:" + value
        : value;
  }

  private static String colorOf(JsonNode object) {
    String color = text(extension(object), "color");
    if (color == null) {
      color = text(object, "x_opencti_color");
    }
    return color != null && HEX_COLOR.matcher(color).matches() ? color : DEFAULT_COLOR;
  }

  private static Integer orderOf(JsonNode object) {
    JsonNode order = extension(object).get("order");
    if (order == null || !order.canConvertToInt()) {
      order = object.get("x_opencti_order");
    }
    return order != null && order.canConvertToInt() && order.asInt() >= 0 ? order.asInt() : 0;
  }

  private static JsonNode extension(JsonNode object) {
    JsonNode extensions = object.get("extensions");
    JsonNode extension = extensions == null ? null : extensions.get(OPENCTI_EXTENSION);
    return extension == null ? EMPTY : extension;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      return null;
    }
    return value.asText().trim();
  }

  private static String upper(String value) {
    return value == null ? null : value.toUpperCase(Locale.ROOT);
  }
}
