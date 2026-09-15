package io.openaev.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.client.url.OcsfSchemaExtension;
import io.openaev.ocsf.parser.generator.emission.ClassMetadata;
import io.openaev.ocsf.parser.schema.source.ReferentialSource;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class DictionaryHelper {
  private final Map<String, ClassMetadata> tracker;
  private final ReferentialSource dictionarySource;

  private final Map<String, String> hardcodedTypeSwaps =
      Map.of(
          "object", "json_t" // the 'object' type is not useful in Java; change it to plain JSON
          );

  public DictionaryHelper(Map<String, ClassMetadata> tracker, ReferentialSource dictionarySource) {
    this.tracker = tracker;
    this.dictionarySource = dictionarySource;
  }

  public String findClassNameFromOcsfAttribute(
      String ocsfAttribute, Optional<OcsfSchemaExtension> extension) throws IOException {
    Stream<Map.Entry<String, JsonNode>> attributeStream =
        dictionarySource.get().get("attributes").propertyStream();

    String fullAttributeName;
    fullAttributeName =
        extension
            .map(ocsfSchemaExtension -> ocsfSchemaExtension.getValue() + "/" + ocsfAttribute)
            .orElse(ocsfAttribute);

    Optional<JsonNode> attribute =
        attributeStream
            .filter(prop -> fullAttributeName.equals(prop.getKey()))
            .findAny()
            .map(Map.Entry::getValue);

    if (attribute.isEmpty()) {
      throw new IllegalArgumentException(
          "OCSF dictionary version %s does not reference a type for attribute name %s."
              .formatted(dictionarySource.getVersion().toString(), fullAttributeName));
    }

    String type = attribute.get().get("type").asText();
    String typeArg =
        "object_t".equals(type) && attribute.get().has("object_type")
            ? tracker
                .get(swap(attribute.get().get("object_type").asText()))
                .fullyQualifiedClassName()
            : tracker.get(type).fullyQualifiedClassName();

    return !attribute.get().has("is_array") || !attribute.get().get("is_array").asBoolean(false)
        ? typeArg
        : java.util.List.class.getCanonicalName() + "<" + typeArg + ">";
  }

  private String swap(String in) {
    if (hardcodedTypeSwaps.containsKey(in)) {
      return hardcodedTypeSwaps.get(in);
    }
    return in;
  }
}
