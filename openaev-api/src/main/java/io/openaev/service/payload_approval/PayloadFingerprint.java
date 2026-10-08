package io.openaev.service.payload_approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.openaev.database.model.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.hibernate.Hibernate;

/**
 * Digest of what a payload runs: its executable content, without its cosmetic fields. Two payloads
 * with the same fingerprint execute the same thing, so an approval bound to a fingerprint covers
 * exactly the content the approver saw.
 *
 * <p>Fail-closed by test: {@code PayloadFingerprintTest} classifies every field of {@link Payload}
 * and its subclasses as either {@link #EXECUTABLE_FIELDS executable} or {@link #COSMETIC_FIELDS
 * cosmetic}, so a field added later breaks the build until someone decides which one it is.
 */
public final class PayloadFingerprint {

  /** Fields that change what runs on an endpoint: any change requires a new approval. */
  public static final Set<String> EXECUTABLE_FIELDS =
      Set.of(
          // Payload
          "type",
          "platforms",
          "executionArch",
          "elevationRequired",
          "arguments",
          "prerequisites",
          "cleanupExecutor",
          "cleanupCommand",
          // Command, AiAttack
          "executor",
          "content",
          // Executable, FileDrop
          "executableFile",
          "fileDropFile",
          // DnsResolution
          "hostname",
          // NetworkTraffic
          "ipSrc",
          "ipDst",
          "portSrc",
          "portDst",
          "protocol",
          // AiAttack
          "engine",
          "category",
          "multiTurn",
          "converters",
          "successDetector");

  /**
   * Fields that describe, classify, evaluate or track a payload without changing what it runs: a
   * change keeps the current approval.
   */
  public static final Set<String> COSMETIC_FIELDS =
      Set.of(
          "id",
          "name",
          "description",
          "externalId",
          "source",
          "status",
          "expectations",
          "expectedSecurityPlatforms",
          "detectionRemediations",
          "outputParsers",
          "tenant",
          "collectorType",
          "authorUser",
          "authorTeam",
          "authorOrganization",
          "lastModifiedBy",
          "approvalStatus",
          "approvedFingerprint",
          "grants",
          "createdAt",
          "updatedAt",
          "resourceType");

  private static final ObjectMapper CANONICAL =
      new ObjectMapper()
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);

  private PayloadFingerprint() {}

  /** SHA-256 (hex) of the executable content of the payload. */
  public static String of(Payload payload) {
    Payload p = (Payload) Hibernate.unproxy(payload);
    Map<String, Object> content = new TreeMap<>();
    content.put("type", p.getType());
    content.put("platforms", sortedNames(p.getPlatforms()));
    content.put("executionArch", p.getExecutionArch());
    content.put("elevationRequired", p.isElevationRequired());
    content.put("cleanupExecutor", p.getCleanupExecutor());
    content.put("cleanupCommand", p.getCleanupCommand());
    content.put("arguments", arguments(p.getArguments()));
    content.put("prerequisites", prerequisites(p.getPrerequisites()));
    switch (p) {
      case Command command -> {
        content.put("executor", command.getExecutor());
        content.put("content", command.getContent());
      }
      case Executable executable ->
          content.put("executableFile", documentId(executable.getExecutableFile()));
      case FileDrop fileDrop -> content.put("fileDropFile", documentId(fileDrop.getFileDropFile()));
      case DnsResolution dns -> content.put("hostname", dns.getHostname());
      case NetworkTraffic traffic -> {
        content.put("ipSrc", traffic.getIpSrc());
        content.put("ipDst", traffic.getIpDst());
        content.put("portSrc", traffic.getPortSrc());
        content.put("portDst", traffic.getPortDst());
        content.put("protocol", traffic.getProtocol());
      }
      case AiAttack ai -> {
        content.put("engine", ai.getEngine());
        content.put("category", ai.getCategory());
        content.put("content", ai.getContent());
        content.put("multiTurn", ai.getMultiTurn());
        content.put("converters", ai.getConverters());
        content.put("successDetector", ai.getSuccessDetector());
      }
      default -> {
        // A payload type unknown to this method is fingerprinted on its common fields only;
        // PayloadFingerprintTest fails when a subclass adds a field this method does not read.
      }
    }
    return sha256(canonicalJson(content));
  }

  private static List<String> sortedNames(Enum<?>[] values) {
    if (values == null) {
      return List.of();
    }
    return Arrays.stream(values).map(Enum::name).sorted().toList();
  }

  // Argument order matters (it is the order the command template reads them); descriptions are
  // help text only.
  private static List<Map<String, Object>> arguments(List<PayloadArgument> arguments) {
    if (arguments == null) {
      return List.of();
    }
    return arguments.stream()
        .map(
            argument -> {
              Map<String, Object> entry = new TreeMap<>();
              entry.put("key", argument.getKey());
              entry.put("type", argument.getType());
              entry.put("defaultValue", argument.getDefaultValue());
              entry.put("separator", argument.getSeparator());
              return entry;
            })
        .toList();
  }

  private static List<Map<String, Object>> prerequisites(List<PayloadPrerequisite> prerequisites) {
    if (prerequisites == null) {
      return List.of();
    }
    return prerequisites.stream()
        .map(
            prerequisite -> {
              Map<String, Object> entry = new TreeMap<>();
              entry.put("executor", prerequisite.getExecutor());
              entry.put("getCommand", prerequisite.getGetCommand());
              entry.put("checkCommand", prerequisite.getCheckCommand());
              return entry;
            })
        .toList();
  }

  private static String documentId(Document document) {
    return document != null ? document.getId() : null;
  }

  private static String canonicalJson(Map<String, Object> content) {
    try {
      return CANONICAL.writeValueAsString(content);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot fingerprint payload content", e);
    }
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
