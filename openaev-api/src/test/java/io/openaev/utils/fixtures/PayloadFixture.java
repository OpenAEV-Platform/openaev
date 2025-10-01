package io.openaev.utils.fixtures;

import static io.openaev.database.model.Command.COMMAND_TYPE;
import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.Payload.PAYLOAD_SOURCE.MANUAL;
import static io.openaev.database.model.Payload.PAYLOAD_STATUS.VERIFIED;

import io.openaev.database.model.*;
import io.openaev.injector_contract.fields.ContractFieldType;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PayloadFixture {

  private static final Endpoint.PLATFORM_TYPE[] LINUX_PLATFORM = {Endpoint.PLATFORM_TYPE.Linux};
  private static final Endpoint.PLATFORM_TYPE[] MACOS_PLATFORM = {Endpoint.PLATFORM_TYPE.MacOS};
  private static final Endpoint.PLATFORM_TYPE[] WINDOWS_PLATFORM = {Endpoint.PLATFORM_TYPE.Windows};
  public static final String COMMAND_PAYLOAD_NAME = "command payload";

  private static void initializeDefaultPayload(
      final Payload payload, final Endpoint.PLATFORM_TYPE[] platforms) {
    payload.setPlatforms(platforms);
    payload.setSource(MANUAL);
    payload.setStatus(VERIFIED);
    payload.setAttackPatterns(Collections.emptyList());
  }

  public static Command createCommand(
      String executor,
      String commandLine,
      @Nullable List<PayloadPrerequisite> prerequisites,
      @Nullable String cleanupCmd) {
    Command command = new Command(UUID.randomUUID().toString(), COMMAND_TYPE, COMMAND_PAYLOAD_NAME);
    command.setContent(commandLine);
    command.setExecutor(executor);
    if (prerequisites != null) {
      command.setPrerequisites(prerequisites);
    }
    if (cleanupCmd != null) {
      command.setCleanupCommand(cleanupCmd);
      command.setCleanupExecutor(executor);
    }
    initializeDefaultPayload(command, WINDOWS_PLATFORM);
    command.setAttackPatterns(Collections.emptyList());
    return command;
  }

  public static Payload createDefaultCommand() {
    return createCommand("PowerShell", "cd ..", null, null);
  }

  public static DetectionRemediation createDetectionRemediation() {
    DetectionRemediation drCS = new DetectionRemediation();
    drCS.setValues("Detection Remediation");
    return drCS;
  }

  public static Payload createDefaultCommandWithPlatformsAndArchitecture(
      Endpoint.PLATFORM_TYPE[] platforms, Payload.PAYLOAD_EXECUTION_ARCH architecture) {
    Payload command = createDefaultCommand();
    command.setPlatforms(platforms);
    command.setExecutionArch(architecture);
    return command;
  }

  public static Payload createDefaultDnsResolution() {
    final DnsResolution dnsResolution =
        new DnsResolution("dns-resolution-id", DNS_RESOLUTION_TYPE, "dns resolution payload");
    dnsResolution.setHostname("localhost");
    initializeDefaultPayload(dnsResolution, LINUX_PLATFORM);
    return dnsResolution;
  }

  public static Payload createDefaultExecutable(Document document) {
    final Executable executable =
        new Executable("executable-id", Executable.EXECUTABLE_TYPE, "executable payload");
    executable.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    executable.setExecutableFile(document);
    initializeDefaultPayload(executable, MACOS_PLATFORM);
    return executable;
  }

  public static Payload createDefaultExecutable() {
    final Executable executable =
        new Executable("executable-id", Executable.EXECUTABLE_TYPE, "executable payload");
    executable.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    initializeDefaultPayload(executable, MACOS_PLATFORM);
    return executable;
  }

  public static Payload createDefaultFileDrop() {
    final FileDrop filedrop =
        new FileDrop("filedrop-id", Executable.EXECUTABLE_TYPE, "filedrop payload");
    filedrop.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    initializeDefaultPayload(filedrop, MACOS_PLATFORM);
    return filedrop;
  }

  public static PayloadArgument createPayloadArgument(
      String key, ContractFieldType type, String defaultValue, String separator) {
    PayloadArgument payloadArgument = new PayloadArgument();
    payloadArgument.setKey(key);
    payloadArgument.setType(type.label);
    payloadArgument.setDefaultValue(defaultValue);
    payloadArgument.setSeparator(separator);
    return payloadArgument;
  }
}
