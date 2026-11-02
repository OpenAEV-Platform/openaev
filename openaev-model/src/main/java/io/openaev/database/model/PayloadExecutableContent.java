package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.hibernate.Hibernate;

/**
 * Snapshot of what a payload runs: the same fields as its approval fingerprint (the executable
 * content), without its cosmetic fields. A pending version stores the edited content as this
 * snapshot; the payload itself keeps its approved content until the version is approved.
 *
 * <p>Files are stored by document id (and name, for display); the type is not stored, a version
 * never changes the type of its payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PayloadExecutableContent(
    @JsonProperty("platforms") List<String> platforms,
    @JsonProperty("execution_arch") String executionArch,
    @JsonProperty("elevation_required") boolean elevationRequired,
    @JsonProperty("cleanup_executor") String cleanupExecutor,
    @JsonProperty("cleanup_command") String cleanupCommand,
    @JsonProperty("arguments") List<PayloadArgument> arguments,
    @JsonProperty("prerequisites") List<PayloadPrerequisite> prerequisites,
    // Command, AiAttack
    @JsonProperty("executor") String executor,
    @JsonProperty("content") String content,
    // Executable, FileDrop
    @JsonProperty("file_id") String fileId,
    @JsonProperty("file_name") String fileName,
    // DnsResolution
    @JsonProperty("hostname") String hostname,
    // NetworkTraffic
    @JsonProperty("ip_src") String ipSrc,
    @JsonProperty("ip_dst") String ipDst,
    @JsonProperty("port_src") Integer portSrc,
    @JsonProperty("port_dst") Integer portDst,
    @JsonProperty("protocol") String protocol,
    // AiAttack
    @JsonProperty("engine") String engine,
    @JsonProperty("category") String category,
    @JsonProperty("multi_turn") Map<String, Object> multiTurn,
    @JsonProperty("converters") List<String> converters,
    @JsonProperty("success_detector") Map<String, Object> successDetector) {

  /** Captures the executable content of a payload (copies, so later edits do not leak in). */
  public static PayloadExecutableContent of(Payload payload) {
    Payload p = (Payload) Hibernate.unproxy(payload);
    String executor = null;
    String content = null;
    Document file = null;
    String hostname = null;
    String ipSrc = null;
    String ipDst = null;
    Integer portSrc = null;
    Integer portDst = null;
    String protocol = null;
    String engine = null;
    String category = null;
    Map<String, Object> multiTurn = null;
    List<String> converters = null;
    Map<String, Object> successDetector = null;
    switch (p) {
      case Command command -> {
        executor = command.getExecutor();
        content = command.getContent();
      }
      case Executable executable -> file = executable.getExecutableFile();
      case FileDrop fileDrop -> file = fileDrop.getFileDropFile();
      case DnsResolution dns -> hostname = dns.getHostname();
      case NetworkTraffic traffic -> {
        ipSrc = traffic.getIpSrc();
        ipDst = traffic.getIpDst();
        portSrc = traffic.getPortSrc();
        portDst = traffic.getPortDst();
        protocol = traffic.getProtocol();
      }
      case AiAttack ai -> {
        engine = ai.getEngine() != null ? ai.getEngine().name() : null;
        category = ai.getCategory();
        content = ai.getContent();
        multiTurn = ai.getMultiTurn() != null ? Map.copyOf(ai.getMultiTurn()) : null;
        converters = ai.getConverters() != null ? List.of(ai.getConverters()) : null;
        successDetector =
            ai.getSuccessDetector() != null ? Map.copyOf(ai.getSuccessDetector()) : null;
      }
      default -> {
        // Other types only carry the common fields.
      }
    }
    return new PayloadExecutableContent(
        p.getPlatforms() != null
            ? Arrays.stream(p.getPlatforms()).map(Enum::name).toList()
            : List.of(),
        p.getExecutionArch() != null ? p.getExecutionArch().name() : null,
        p.isElevationRequired(),
        p.getCleanupExecutor(),
        p.getCleanupCommand(),
        p.getArguments() != null ? copyArguments(p.getArguments()) : List.of(),
        p.getPrerequisites() != null ? copyPrerequisites(p.getPrerequisites()) : List.of(),
        executor,
        content,
        file != null ? file.getId() : null,
        file != null ? file.getName() : null,
        hostname,
        ipSrc,
        ipDst,
        portSrc,
        portDst,
        protocol,
        engine,
        category,
        multiTurn,
        converters,
        successDetector);
  }

  /**
   * Writes this content onto a payload of the same type. The file is resolved by the caller (by id)
   * and passed in; it is ignored for types without a file.
   */
  public void applyTo(Payload payload, Document file) {
    Payload p = (Payload) Hibernate.unproxy(payload);
    p.setPlatforms(
        platforms == null
            ? new Endpoint.PLATFORM_TYPE[0]
            : platforms.stream()
                .map(Endpoint.PLATFORM_TYPE::valueOf)
                .toArray(Endpoint.PLATFORM_TYPE[]::new));
    if (executionArch != null) {
      p.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.valueOf(executionArch));
    }
    p.setElevationRequired(elevationRequired);
    p.setCleanupExecutor(cleanupExecutor);
    p.setCleanupCommand(cleanupCommand);
    p.setArguments(arguments == null ? new ArrayList<>() : copyArguments(arguments));
    p.setPrerequisites(
        prerequisites == null ? new ArrayList<>() : copyPrerequisites(prerequisites));
    switch (p) {
      case Command command -> {
        command.setExecutor(executor);
        command.setContent(content);
      }
      case Executable executable -> executable.setExecutableFile(file);
      case FileDrop fileDrop -> fileDrop.setFileDropFile(file);
      case DnsResolution dns -> dns.setHostname(hostname);
      case NetworkTraffic traffic -> {
        traffic.setIpSrc(ipSrc);
        traffic.setIpDst(ipDst);
        traffic.setPortSrc(portSrc);
        traffic.setPortDst(portDst);
        traffic.setProtocol(protocol);
      }
      case AiAttack ai -> {
        if (engine != null) {
          ai.setEngine(AiAttack.AI_ATTACK_ENGINE.valueOf(engine));
        }
        ai.setCategory(category);
        ai.setContent(content);
        ai.setMultiTurn(multiTurn != null ? new java.util.HashMap<>(multiTurn) : null);
        ai.setConverters(converters != null ? converters.toArray(String[]::new) : null);
        ai.setSuccessDetector(
            successDetector != null ? new java.util.HashMap<>(successDetector) : null);
      }
      default -> {
        // Other types only carry the common fields.
      }
    }
  }

  private static List<PayloadArgument> copyArguments(List<PayloadArgument> source) {
    List<PayloadArgument> copy = new ArrayList<>();
    for (PayloadArgument argument : source) {
      PayloadArgument clone = new PayloadArgument();
      clone.setType(argument.getType());
      clone.setKey(argument.getKey());
      clone.setDefaultValue(argument.getDefaultValue());
      clone.setSeparator(argument.getSeparator());
      clone.setDescription(argument.getDescription());
      copy.add(clone);
    }
    return copy;
  }

  private static List<PayloadPrerequisite> copyPrerequisites(List<PayloadPrerequisite> source) {
    List<PayloadPrerequisite> copy = new ArrayList<>();
    for (PayloadPrerequisite prerequisite : source) {
      PayloadPrerequisite clone = new PayloadPrerequisite();
      clone.setExecutor(prerequisite.getExecutor());
      clone.setGetCommand(prerequisite.getGetCommand());
      clone.setCheckCommand(prerequisite.getCheckCommand());
      clone.setDescription(prerequisite.getDescription());
      copy.add(clone);
    }
    return copy;
  }
}
