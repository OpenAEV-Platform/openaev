package io.openaev.ocsf.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.schema.v190.OcsfClassUid;
import java.util.ArrayList;
import java.util.List;

public class OcsfFilter {
  public List<JsonNode> getClassObjectsByUid(OcsfClassUid uid, List<JsonNode> nodes) {
    List<JsonNode> selected = new ArrayList<>();
    for (JsonNode node : nodes) {
      if (node.has("class_uid")
          && OcsfClassUid.fromClassUid(node.get("class_uid").asText()).equals(uid)) {
        selected.add(node);
      }
    }
    return selected;
  }
}
