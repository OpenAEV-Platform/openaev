package io.openaev.importer;

import static java.util.Spliterators.spliteratorUnknownSize;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.service.ImportEntry;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Importer {

  void importData(
      JsonNode importNode,
      Map<String, ImportEntry> docReferences,
      Exercise exercise,
      Scenario scenario);

  default Stream<JsonNode> resolveJsonElements(JsonNode node, String key) {
    JsonNode dataNode = node.get(key);
    if (dataNode == null) {
      return Stream.empty();
    }
    Iterator<JsonNode> elements = dataNode.elements();
    Spliterator<JsonNode> elementsSplit = spliteratorUnknownSize(elements, Spliterator.ORDERED);
    return StreamSupport.stream(elementsSplit, false);
  }

  default List<String> resolveJsonIds(JsonNode node, String key) {
    return resolveJsonElements(node, key).map(JsonNode::asText).toList();
  }
}
