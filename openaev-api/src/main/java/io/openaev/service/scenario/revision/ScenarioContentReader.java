package io.openaev.service.scenario.revision;

import static io.openaev.service.scenario.revision.ScenarioMerge.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * Reads a scenario in the normalized format the merge compares (ADR-018, section 4): from a
 * catalogue package or from the tenant's entities. Configuration is left out; a payload contract
 * becomes its (repository, external ID, version), a local one keeps its contract ID.
 */
@Component
@RequiredArgsConstructor
public class ScenarioContentReader {

  public static final String PAYLOAD_PREFIX = "payload:";
  public static final String CONTRACT_PREFIX = "contract:";
  public static final String INJECT_IDS = "inject_ids";
  public static final String STEP_IDS = "step_ids";

  private final ObjectMapper mapper;

  // -- PACKAGE --

  /**
   * The content of a package. {@code payloadVersions} maps a payload external ID to the normalized
   * contract of the version its release names.
   */
  public ObjectNode fromPackage(JsonNode root, Map<String, String> payloadVersions) {
    ObjectNode content = mapper.createObjectNode();
    JsonNode information = root.path("scenario_information");
    content.set(
        SCENARIO,
        scenarioBlock(
            text(information, "scenario_name"),
            text(information, "scenario_description"),
            text(information, "scenario_severity"),
            text(information, "scenario_category")));
    content.set(MATERIALS, materials(packageMaterials(root)));
    ObjectNode variables = mapper.createObjectNode();
    root.path("scenario_variables")
        .forEach(v -> variables.set(text(v, "variable_key"), v.path("variable_value")));
    content.set(VARIABLES, variables);

    Map<String, String> keys = new HashMap<>();
    root.path("scenario_injects").forEach(i -> keys.put(text(i, "inject_id"), packageKey(i)));
    ObjectNode injects = mapper.createObjectNode();
    for (JsonNode node : root.path("scenario_injects")) {
      JsonNode contract = node.path("inject_injector_contract");
      JsonNode payload = contract.path("injector_contract_payload");
      String normalized;
      Set<String> argumentKeys = new HashSet<>();
      if (payload.isObject()) {
        String externalId = text(payload, "payload_external_id");
        normalized =
            externalId != null && payloadVersions.containsKey(externalId)
                ? payloadVersions.get(externalId)
                : "payload-name:" + text(payload, "payload_name");
        payload.path("payload_arguments").forEach(a -> argumentKeys.add(text(a, "key")));
      } else {
        normalized = CONTRACT_PREFIX + text(contract, "injector_contract_id");
      }
      List<ObjectNode> dependsOn = new ArrayList<>();
      node.path("inject_depends_on")
          .forEach(
              d -> {
                String parent =
                    keys.get(text(d.path("dependency_relationship"), "inject_parent_id"));
                if (parent != null) {
                  dependsOn.add(dependency(parent, d.get("dependency_condition")));
                }
              });
      injects.set(
          packageKey(node),
          inject(
              normalized,
              node.get("inject_content"),
              argumentKeys,
              node.path("inject_depends_duration").asLong(0),
              dependsOn,
              text(node, "inject_title"),
              text(node, "inject_description")));
    }
    content.set(INJECTS, injects);
    content.set(STEPS, mapper.createObjectNode());
    return canonical(content);
  }

  /**
   * Package keys of the injects that point to challenges, channel articles or documents by the
   * package's own IDs, which only an import remaps.
   */
  public Set<String> keysWithReferences(JsonNode root) {
    Set<String> keys = new HashSet<>();
    for (JsonNode node : root.path("scenario_injects")) {
      JsonNode content = node.path("inject_content");
      boolean documentArgument = false;
      for (JsonNode argument :
          node.path("inject_injector_contract")
              .path("injector_contract_payload")
              .path("payload_arguments")) {
        String key = text(argument, "key");
        documentArgument |=
            "document".equals(text(argument, "type"))
                && key != null
                && !content.path(key).asText("").isEmpty();
      }
      if (!content.path("challenges").isEmpty()
          || !content.path("articles").isEmpty()
          || !node.path("inject_documents").isEmpty()
          || documentArgument) {
        keys.add(packageKey(node));
      }
    }
    return keys;
  }

  private static String packageKey(JsonNode inject) {
    String catalogId = text(inject, "inject_catalog_id");
    return catalogId != null ? catalogId : text(inject, "inject_id");
  }

  private ObjectNode packageMaterials(JsonNode root) {
    ObjectNode materials = mapper.createObjectNode();
    ArrayNode articles = materials.putArray("articles");
    root.path("scenario_articles")
        .forEach(
            a ->
                articles.add(
                    article(
                        text(a, "article_name"),
                        text(a, "article_content"),
                        text(a, "article_author"))));
    ArrayNode objectives = materials.putArray("objectives");
    root.path("scenario_objectives")
        .forEach(
            o ->
                objectives.add(
                    objective(
                        text(o, "objective_title"),
                        text(o, "objective_description"),
                        o.path("objective_priority").asInt(0))));
    ArrayNode lessons = materials.putArray("lessons");
    root.path("scenario_lessons_categories")
        .forEach(
            c -> {
              String categoryId = text(c, "lessonscategory_id");
              List<ObjectNode> questions = new ArrayList<>();
              root.path("scenario_lessons_questions")
                  .forEach(
                      q -> {
                        if (Objects.equals(categoryId, text(q, "lessons_question_category"))) {
                          questions.add(
                              question(
                                  text(q, "lessons_question_content"),
                                  text(q, "lessons_question_explanation"),
                                  q.path("lessons_question_order").asInt(0)));
                        }
                      });
              lessons.add(
                  lesson(
                      text(c, "lessons_category_name"),
                      text(c, "lessons_category_description"),
                      c.path("lessons_category_order").asInt(0),
                      questions));
            });
    return materials;
  }

  // -- ENTITIES --

  /** The local state: the scenario, its variables and its injects as they are now. */
  public ObjectNode fromEntities(
      Scenario scenario, Collection<Inject> injects, Collection<Variable> variables) {
    ObjectNode content = mapper.createObjectNode();
    content.set(
        SCENARIO,
        scenarioBlock(
            scenario.getName(),
            scenario.getDescription(),
            scenario.getSeverity() == null ? null : scenario.getSeverity().name(),
            scenario.getCategory()));
    content.set(MATERIALS, materials(entityMaterials(scenario)));
    ObjectNode vars = mapper.createObjectNode();
    variables.forEach(v -> vars.put(v.getKey(), v.getValue()));
    content.set(VARIABLES, vars);
    ObjectNode nodes = mapper.createObjectNode();
    injects.forEach(i -> nodes.set(key(i), injectOf(i)));
    content.set(INJECTS, nodes);
    content.set(STEPS, mapper.createObjectNode());
    return canonical(content);
  }

  public static String key(Inject inject) {
    return inject.getCatalogId() != null ? inject.getCatalogId() : LOCAL_PREFIX + inject.getId();
  }

  public ObjectNode injectOf(Inject inject) {
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    Payload payload = contract == null ? null : contract.getPayload();
    Set<String> argumentKeys = new HashSet<>();
    if (payload != null) {
      Optional.ofNullable(payload.getArguments())
          .orElse(List.of())
          .forEach(a -> argumentKeys.add(a.getKey()));
    }
    List<ObjectNode> dependsOn = new ArrayList<>();
    Optional.ofNullable(inject.getDependsOn())
        .orElse(List.of())
        .forEach(
            d ->
                dependsOn.add(
                    dependency(
                        key(d.getCompositeId().getInjectParent()),
                        mapper.valueToTree(d.getInjectDependencyCondition()))));
    return canonical(
        inject(
            contractOf(contract),
            inject.getContent(),
            argumentKeys,
            inject.getDependsDuration() == null ? 0 : inject.getDependsDuration(),
            dependsOn,
            inject.getTitle(),
            inject.getDescription()));
  }

  public static String contractOf(InjectorContract contract) {
    if (contract == null) {
      return null;
    }
    Payload payload = contract.getPayload();
    if (payload != null && payload.isManaged()) {
      return payloadContract(
          payload.getIdentity().getRepository(), payload.getExternalId(), payload.getVersion());
    }
    return CONTRACT_PREFIX + contract.getId();
  }

  public static String payloadContract(String repository, String externalId, String version) {
    return PAYLOAD_PREFIX + repository + "|" + externalId + "|" + version;
  }

  private ObjectNode entityMaterials(Scenario scenario) {
    ObjectNode materials = mapper.createObjectNode();
    ArrayNode articles = materials.putArray("articles");
    Optional.ofNullable(scenario.getArticles())
        .orElse(List.of())
        .forEach(a -> articles.add(article(a.getName(), a.getContent(), a.getAuthor())));
    ArrayNode objectives = materials.putArray("objectives");
    Optional.ofNullable(scenario.getObjectives())
        .orElse(List.of())
        .forEach(
            o ->
                objectives.add(
                    objective(
                        o.getTitle(),
                        o.getDescription(),
                        o.getPriority() == null ? 0 : o.getPriority())));
    ArrayNode lessons = materials.putArray("lessons");
    Optional.ofNullable(scenario.getLessonsCategories())
        .orElse(List.of())
        .forEach(
            c ->
                lessons.add(
                    lesson(
                        c.getName(),
                        c.getDescription(),
                        c.getOrder(),
                        c.getQuestions().stream()
                            .map(q -> question(q.getContent(), q.getExplanation(), q.getOrder()))
                            .toList())));
    return materials;
  }

  // -- NORMALIZED PIECES --

  private ObjectNode scenarioBlock(
      String name, String description, String severity, String category) {
    ObjectNode block = mapper.createObjectNode();
    block.put("name", name);
    block.put("description", description);
    block.put("severity", severity == null ? null : severity.toLowerCase(Locale.ROOT));
    block.put("category", category);
    return block;
  }

  private ObjectNode inject(
      String contract,
      JsonNode rawContent,
      Set<String> argumentKeys,
      long dependsDuration,
      List<ObjectNode> dependsOn,
      String title,
      String description) {
    ObjectNode element = mapper.createObjectNode();
    element.put(CONTRACT, contract);
    ObjectNode content = mapper.createObjectNode();
    ObjectNode arguments = mapper.createObjectNode();
    if (rawContent instanceof ObjectNode raw) {
      raw.properties()
          .forEach(
              e -> {
                if (argumentKeys.contains(e.getKey())) {
                  arguments.set(e.getKey(), e.getValue());
                } else if (!ScenarioConfigFields.INJECT_CONTENT_KEYS.contains(e.getKey())) {
                  content.set(e.getKey(), e.getValue());
                }
              });
    }
    element.set(CONTENT, content);
    ObjectNode timing = mapper.createObjectNode();
    timing.put("depends_duration", dependsDuration);
    ArrayNode depends = timing.putArray(DEPENDS_ON);
    dependsOn.stream()
        .sorted(Comparator.comparing(d -> d.path(PARENT).asText()))
        .forEach(depends::add);
    element.set(TIMING, timing);
    ObjectNode text = mapper.createObjectNode();
    text.put("title", title);
    text.put("description", description);
    element.set(TEXT, text);
    element.set(ARGUMENTS, arguments);
    return element;
  }

  private ObjectNode dependency(String parent, JsonNode condition) {
    ObjectNode dependency = mapper.createObjectNode();
    dependency.put(PARENT, parent);
    dependency.set("condition", condition == null ? mapper.nullNode() : condition);
    return dependency;
  }

  private ObjectNode article(String name, String content, String author) {
    ObjectNode node = mapper.createObjectNode();
    node.put("name", name);
    node.put("content", content);
    node.put("author", author);
    return node;
  }

  private ObjectNode objective(String title, String description, int priority) {
    ObjectNode node = mapper.createObjectNode();
    node.put("title", title);
    node.put("description", description);
    node.put("priority", priority);
    return node;
  }

  private ObjectNode question(String content, String explanation, int order) {
    ObjectNode node = mapper.createObjectNode();
    node.put("content", content);
    node.put("explanation", explanation);
    node.put("order", order);
    return node;
  }

  private ObjectNode lesson(String name, String description, int order, List<ObjectNode> qs) {
    ObjectNode node = mapper.createObjectNode();
    node.put("name", name);
    node.put("description", description);
    node.put("order", order);
    ArrayNode questions = node.putArray("questions");
    qs.stream().sorted(Comparator.comparing(JsonNode::toString)).forEach(questions::add);
    return node;
  }

  /** Articles, lessons and objectives are one block in step 1: compared by their hash. */
  private ObjectNode materials(ObjectNode materials) {
    ObjectNode block = mapper.createObjectNode();
    for (String field : List.of("articles", "objectives", "lessons")) {
      List<String> items = new ArrayList<>();
      materials.path(field).forEach(item -> items.add(canonical(item).toString()));
      Collections.sort(items);
      block.put(field, sha256(String.join("\n", items)));
    }
    return block;
  }

  /** Same JSON, same tree: numbers and key order no longer depend on where the value came from. */
  @SuppressWarnings("unchecked")
  public <T extends JsonNode> T canonical(T node) {
    try {
      return (T) mapper.readTree(mapper.writeValueAsString(sortKeys(node)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode sortKeys(JsonNode node) {
    if (node instanceof ObjectNode object) {
      ObjectNode sorted = mapper.createObjectNode();
      new TreeMap<>(collect(object)).forEach((k, v) -> sorted.set(k, sortKeys(v)));
      return sorted;
    }
    if (node instanceof ArrayNode array) {
      ArrayNode copy = mapper.createArrayNode();
      array.forEach(item -> copy.add(sortKeys(item)));
      return copy;
    }
    return node;
  }

  private static Map<String, JsonNode> collect(ObjectNode object) {
    Map<String, JsonNode> map = new HashMap<>();
    object.properties().forEach(e -> map.put(e.getKey(), e.getValue()));
    return map;
  }

  private static String sha256(String value) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  static Payload unproxy(Payload payload) {
    return (Payload) Hibernate.unproxy(payload);
  }
}
