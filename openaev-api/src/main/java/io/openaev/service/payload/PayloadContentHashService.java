package io.openaev.service.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.openaev.database.model.*;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.FileService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/**
 * Canonical content hash of a payload (RFC 0005, Appendix B): SHA-256 of its RFC 8785 JSON, so the
 * same content gives the same hash on any platform and in the repositories' CI.
 */
@Service
@RequiredArgsConstructor
public class PayloadContentHashService {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DOCUMENT_ARGUMENT = "document";

  private final FileService fileService;
  private final DocumentService documentService;

  // document_target is content-addressed, so a cached file hash never goes stale.
  private final Cache<String, String> fileHashes =
      Caffeine.newBuilder().maximumSize(10_000).build();

  /** Null when an attached file cannot be read: such a payload is never linked by content. */
  public String hash(Payload payload, Collection<AttackPattern> attackPatterns) {
    try {
      ObjectNode canonical = canonicalTree(payload, attackPatterns);
      String json = new JsonCanonicalizer(canonical.toString()).getEncodedString();
      return DigestUtils.sha256Hex(json.getBytes(StandardCharsets.UTF_8));
    } catch (MissingFileException e) {
      return null;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  ObjectNode canonicalTree(Payload source, Collection<AttackPattern> attackPatterns) {
    Payload payload = (Payload) Hibernate.unproxy(source);
    ObjectNode node = MAPPER.createObjectNode();
    put(node, "payload_type", payload.getType());
    put(node, "payload_name", payload.getName());
    put(node, "payload_description", payload.getDescription());
    node.set("payload_platforms", sortedValues(payload.getPlatforms()));
    put(node, "payload_execution_arch", payload.getExecutionArch());
    node.put("payload_elevation_required", payload.isElevationRequired());
    node.set("payload_expectations", sortedValues(payload.getExpectations()));
    node.set("payload_expected_security_platforms", expectedSecurityPlatforms(payload));
    node.set("payload_arguments", arguments(payload));
    node.set("payload_prerequisites", MAPPER.valueToTree(payload.getPrerequisites()));
    put(node, "payload_cleanup_executor", payload.getCleanupExecutor());
    put(node, "payload_cleanup_command", payload.getCleanupCommand());
    typeSpecific(node, payload);
    node.set(
        "payload_attack_patterns",
        sortedValues(
            attackPatterns == null
                ? null
                : attackPatterns.stream().map(AttackPattern::getExternalId).toArray()));
    node.set("payload_detection_remediations", detectionRemediations(payload));
    node.set("payload_output_parsers", outputParsers(payload));
    return (ObjectNode) withoutEmpty(node);
  }

  private void typeSpecific(ObjectNode node, Payload payload) {
    switch (payload) {
      case Command command -> {
        put(node, "command_executor", command.getExecutor());
        put(node, "command_content", command.getContent());
      }
      case DnsResolution dns -> put(node, "dns_resolution_hostname", dns.getHostname());
      case NetworkTraffic traffic -> {
        put(node, "network_traffic_ip_src", traffic.getIpSrc());
        put(node, "network_traffic_ip_dst", traffic.getIpDst());
        put(node, "network_traffic_port_src", traffic.getPortSrc());
        put(node, "network_traffic_port_dst", traffic.getPortDst());
        put(node, "network_traffic_protocol", traffic.getProtocol());
      }
      case AiAttack attack -> {
        put(node, "ai_attack_engine", attack.getEngine());
        put(node, "ai_attack_category", attack.getCategory());
        put(node, "ai_attack_content", attack.getContent());
        node.set("ai_attack_multi_turn", MAPPER.valueToTree(attack.getMultiTurn()));
        node.set("ai_attack_converters", MAPPER.valueToTree(attack.getConverters()));
        node.set("ai_attack_success_detector", MAPPER.valueToTree(attack.getSuccessDetector()));
      }
      case Executable executable ->
          put(node, "executable_file", fileHash(executable.getExecutableFile()));
      case FileDrop fileDrop -> put(node, "file_drop_file", fileHash(fileDrop.getFileDropFile()));
      default -> {}
    }
  }

  // Ordered: argument order is meaningful. A document argument refers to its file by hash.
  private ArrayNode arguments(Payload payload) {
    ArrayNode arguments = MAPPER.createArrayNode();
    for (PayloadArgument argument : Optional.ofNullable(payload.getArguments()).orElse(List.of())) {
      ObjectNode node = MAPPER.valueToTree(argument);
      if (DOCUMENT_ARGUMENT.equals(node.path("type").asText())
          && argument.getDefaultValue() != null) {
        node.put("default_value", fileHash(documentOf(argument.getDefaultValue())));
      }
      arguments.add(node);
    }
    return arguments;
  }

  private ObjectNode expectedSecurityPlatforms(Payload payload) {
    ObjectNode node = MAPPER.createObjectNode();
    Optional.ofNullable(payload.getExpectedSecurityPlatforms())
        .orElse(Map.of())
        .forEach(
            (expectation, platforms) ->
                node.set(
                    MAPPER.valueToTree(expectation).asText(),
                    sortedValues(platforms == null ? null : platforms.toArray())));
    return node;
  }

  private ArrayNode detectionRemediations(Payload payload) {
    List<JsonNode> remediations = new ArrayList<>();
    for (DetectionRemediation remediation :
        Optional.ofNullable(payload.getDetectionRemediations()).orElse(List.of())) {
      ObjectNode node = MAPPER.createObjectNode();
      SecurityPlatform platform = remediation.getSecurityPlatform();
      put(
          node,
          "security_platform_type",
          platform == null ? null : platform.getSecurityPlatformType());
      put(node, "detection_remediation_values", remediation.getValues());
      remediations.add(node);
    }
    return sortedNodes(remediations);
  }

  private ArrayNode outputParsers(Payload payload) {
    List<JsonNode> parsers = new ArrayList<>();
    for (OutputParser parser : Optional.ofNullable(payload.getOutputParsers()).orElse(Set.of())) {
      List<JsonNode> elements = new ArrayList<>();
      for (ContractOutputElement element : parser.getContractOutputElements()) {
        List<JsonNode> groups = new ArrayList<>();
        for (RegexGroup group : element.getRegexGroups()) {
          ObjectNode groupNode = MAPPER.createObjectNode();
          put(groupNode, "regex_group_field", group.getField());
          put(groupNode, "regex_group_index_values", group.getIndexValues());
          groups.add(groupNode);
        }
        ObjectNode elementNode = MAPPER.createObjectNode();
        put(elementNode, "contract_output_element_key", element.getKey());
        put(elementNode, "contract_output_element_name", element.getName());
        put(elementNode, "contract_output_element_rule", element.getRule());
        put(elementNode, "contract_output_element_type", element.getType());
        elementNode.put("contract_output_element_is_finding", element.isFinding());
        elementNode.set("contract_output_element_regex_groups", sortedNodes(groups));
        elements.add(elementNode);
      }
      ObjectNode parserNode = MAPPER.createObjectNode();
      put(parserNode, "output_parser_mode", parser.getMode());
      put(parserNode, "output_parser_type", parser.getType());
      parserNode.set("output_parser_contract_output_elements", sortedNodes(elements));
      parsers.add(parserNode);
    }
    return sortedNodes(parsers);
  }

  private Document documentOf(String documentId) {
    try {
      return documentService.document(documentId);
    } catch (ElementNotFoundException e) {
      throw new MissingFileException();
    }
  }

  private String fileHash(Document document) {
    if (document == null) {
      return null;
    }
    String tenantId = document.getTenant() == null ? null : document.getTenant().getId();
    return fileHashes.get(
        tenantId + "/" + document.getTarget(),
        key -> {
          try (InputStream content =
              fileService.getFile(document, tenantId).orElseThrow(MissingFileException::new)) {
            return DigestUtils.sha256Hex(content);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  private static void put(ObjectNode node, String key, Object value) {
    if (value != null) {
      node.set(key, MAPPER.valueToTree(value));
    }
  }

  private static ArrayNode sortedValues(Object[] values) {
    ArrayNode array = MAPPER.createArrayNode();
    if (values != null) {
      Arrays.stream(values)
          .filter(Objects::nonNull)
          .map(value -> MAPPER.valueToTree(value).asText())
          .sorted()
          .forEach(array::add);
    }
    return array;
  }

  // Sets of objects: sorted by their canonical form, so storage order never changes the hash.
  private static ArrayNode sortedNodes(List<JsonNode> nodes) {
    ArrayNode array = MAPPER.createArrayNode();
    nodes.stream()
        .map(PayloadContentHashService::withoutEmpty)
        .sorted(Comparator.comparing(PayloadContentHashService::canonical))
        .forEach(array::add);
    return array;
  }

  private static String canonical(JsonNode node) {
    try {
      return new JsonCanonicalizer(node.toString()).getEncodedString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // Appendix B: null and empty fields are left out.
  static JsonNode withoutEmpty(JsonNode node) {
    if (node.isObject()) {
      ObjectNode cleaned = MAPPER.createObjectNode();
      node.properties()
          .forEach(
              entry -> {
                JsonNode value = withoutEmpty(entry.getValue());
                if (!isEmpty(value)) {
                  cleaned.set(entry.getKey(), value);
                }
              });
      return cleaned;
    }
    if (node.isArray()) {
      ArrayNode cleaned = MAPPER.createArrayNode();
      node.forEach(item -> cleaned.add(withoutEmpty(item)));
      return cleaned;
    }
    return node;
  }

  private static boolean isEmpty(JsonNode node) {
    return node == null
        || node.isNull()
        || (node.isTextual() && node.asText().isEmpty())
        || (node.isContainerNode() && node.isEmpty());
  }

  private static final class MissingFileException extends RuntimeException {}
}
