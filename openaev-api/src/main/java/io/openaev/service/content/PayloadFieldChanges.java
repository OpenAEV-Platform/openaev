package io.openaev.service.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.hibernate.Hibernate;

/**
 * The fields that differ between two versions of a payload, each with its value before and after.
 */
public final class PayloadFieldChanges {

  private PayloadFieldChanges() {}

  public static ArrayNode between(ObjectMapper mapper, Payload before, Payload after) {
    Map<String, JsonNode> old = fields(mapper, before);
    Map<String, JsonNode> current = fields(mapper, after);
    ArrayNode changes = mapper.createArrayNode();
    current.forEach(
        (field, value) -> {
          JsonNode previous = old.get(field);
          if (!Objects.equals(previous, value)) {
            ObjectNode change = changes.addObject();
            change.put("field", field);
            change.set("before", previous == null ? mapper.nullNode() : previous);
            change.set("after", value);
          }
        });
    return changes;
  }

  private static Map<String, JsonNode> fields(ObjectMapper mapper, Payload payload) {
    Payload p = (Payload) Hibernate.unproxy(payload);
    Map<String, JsonNode> fields = new LinkedHashMap<>();
    fields.put("payload_name", mapper.valueToTree(p.getName()));
    fields.put("payload_description", mapper.valueToTree(p.getDescription()));
    fields.put("payload_platforms", mapper.valueToTree(p.getPlatforms()));
    fields.put("payload_execution_arch", mapper.valueToTree(p.getExecutionArch()));
    if (p instanceof Command command) {
      fields.put("command_executor", mapper.valueToTree(command.getExecutor()));
      fields.put("command_content", mapper.valueToTree(command.getContent()));
    }
    if (p instanceof DnsResolution dns) {
      fields.put("dns_resolution_hostname", mapper.valueToTree(dns.getHostname()));
    }
    if (p instanceof Executable executable) {
      fields.put("executable_file", documentName(mapper, executable.getExecutableFile()));
    }
    if (p instanceof FileDrop fileDrop) {
      fields.put("file_drop_file", documentName(mapper, fileDrop.getFileDropFile()));
    }
    fields.put("payload_arguments", mapper.valueToTree(p.getArguments()));
    fields.put("payload_prerequisites", mapper.valueToTree(p.getPrerequisites()));
    fields.put("payload_cleanup_executor", mapper.valueToTree(p.getCleanupExecutor()));
    fields.put("payload_cleanup_command", mapper.valueToTree(p.getCleanupCommand()));
    return fields;
  }

  private static JsonNode documentName(ObjectMapper mapper, Document document) {
    return document == null ? mapper.nullNode() : mapper.valueToTree(document.getName());
  }
}
