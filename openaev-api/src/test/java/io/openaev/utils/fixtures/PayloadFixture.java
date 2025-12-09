package io.openaev.utils.fixtures;

import static io.openaev.database.model.Command.COMMAND_TYPE;
import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.Payload.PAYLOAD_SOURCE.MANUAL;
import static io.openaev.database.model.Payload.PAYLOAD_STATUS.VERIFIED;

import io.openaev.database.model.*;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.utils.fixtures.composers.DomainComposer;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PayloadFixture {

  private static final Endpoint.PLATFORM_TYPE[] LINUX_PLATFORM = {Endpoint.PLATFORM_TYPE.Linux};
  private static final Endpoint.PLATFORM_TYPE[] MACOS_PLATFORM = {Endpoint.PLATFORM_TYPE.MacOS};
  private static final Endpoint.PLATFORM_TYPE[] WINDOWS_PLATFORM = {Endpoint.PLATFORM_TYPE.Windows};
  public static final String COMMAND_PAYLOAD_NAME = "command payload";
  private DomainComposer domainComposer;

  private static void initializeDefaultPayload(
      final Payload payload, final Endpoint.PLATFORM_TYPE[] platforms, Set<Domain> domains) {
    payload.setPlatforms(platforms);
    payload.setSource(MANUAL);
    payload.setStatus(VERIFIED);
    payload.setAttackPatterns(Collections.emptyList());
    payload.setDomains(domains);
  }

  public static Command createCommand(
      String executor,
      String commandLine,
      @Nullable List<PayloadPrerequisite> prerequisites,
      @Nullable String cleanupCmd,
      Set<Domain> domains) {
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
    initializeDefaultPayload(command, WINDOWS_PLATFORM, domains);
    command.setDomains(domains);
    command.setAttackPatterns(Collections.emptyList());
    return command;
  }

  public static Payload createDefaultCommand(Set<Domain> domains) {
    return createCommand("PowerShell", "cd ..", null, null, domains);
  }

  public static DetectionRemediation createDetectionRemediation() {
    DetectionRemediation drCS = new DetectionRemediation();
    drCS.setValues("Detection Remediation");
    return drCS;
  }

  public static Payload createDefaultCommandWithPlatformsAndArchitecture(
      Endpoint.PLATFORM_TYPE[] platforms,
      Payload.PAYLOAD_EXECUTION_ARCH architecture,
      Set<Domain> domains) {
    Payload command = createDefaultCommand(domains);
    command.setPlatforms(platforms);
    command.setExecutionArch(architecture);
    return command;
  }

  public static Payload createDefaultCommandWithAttackPatternAndArguments(
      List<AttackPattern> attackPatterns, List<PayloadArgument> arguments, Set<Domain> domains) {
    Payload command = createDefaultCommand(domains);
    command.setPlatforms(LINUX_PLATFORM);
    command.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    command.setAttackPatterns(attackPatterns);
    command.setArguments(arguments);
    return command;
  }

  public static Payload createDefaultDnsResolution(Set<Domain> domains) {
    final DnsResolution dnsResolution =
        new DnsResolution("dns-resolution-id", DNS_RESOLUTION_TYPE, "dns resolution payload");
    dnsResolution.setHostname("localhost");
    initializeDefaultPayload(dnsResolution, LINUX_PLATFORM, domains);
    return dnsResolution;
  }

  public static Payload createDefaultDnsResolutionWithAttackPatternAndArguments(
      List<AttackPattern> attackPatterns, List<PayloadArgument> arguments, Set<Domain> domains) {

    final DnsResolution dnsResolution =
        new DnsResolution("dns-resolution-id", DNS_RESOLUTION_TYPE, "dns resolution payload");
    dnsResolution.setHostname("localhost");
    initializeDefaultPayload(dnsResolution, LINUX_PLATFORM, domains);
    dnsResolution.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    dnsResolution.setAttackPatterns(attackPatterns);
    dnsResolution.setArguments(arguments);
    dnsResolution.setDomains(domains);

    return dnsResolution;
  }

  public static Payload createDefaultExecutable(Document document, Set<Domain> domains) {
    final Executable executable =
        new Executable("executable-id", Executable.EXECUTABLE_TYPE, "executable payload");
    executable.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    executable.setExecutableFile(document);
    initializeDefaultPayload(executable, MACOS_PLATFORM, domains);
    return executable;
  }

  public static Payload createDefaultExecutable(Set<Domain> domains) {
    final Executable executable =
        new Executable("executable-id", Executable.EXECUTABLE_TYPE, "executable payload");
    executable.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    initializeDefaultPayload(executable, MACOS_PLATFORM, domains);
    return executable;
  }

  public static Payload createDefaultExecutableWithAttackPatternAndArguments(
      List<AttackPattern> attackPatterns, List<PayloadArgument> arguments, Set<Domain> domains) {
    final Executable executable =
        new Executable("executable-id", Executable.EXECUTABLE_TYPE, "executable payload");
    executable.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    initializeDefaultPayload(executable, MACOS_PLATFORM, domains);
    executable.setAttackPatterns(attackPatterns);
    executable.setArguments(arguments);
    executable.setDomains(domains);
    return executable;
  }

  public static Payload createDefaultFileDrop(Set<Domain> domains) {
    final FileDrop filedrop =
        new FileDrop("filedrop-id", Executable.EXECUTABLE_TYPE, "filedrop payload");
    filedrop.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    initializeDefaultPayload(filedrop, MACOS_PLATFORM, domains);
    return filedrop;
  }

  public static Payload createDefaultFileDropWithAttackPatternAndArguments(
      List<AttackPattern> attackPatterns, List<PayloadArgument> arguments, Set<Domain> domains) {
    final FileDrop filedrop =
        new FileDrop("filedrop-id", Executable.EXECUTABLE_TYPE, "filedrop payload");
    filedrop.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    initializeDefaultPayload(filedrop, MACOS_PLATFORM, domains);
    filedrop.setAttackPatterns(attackPatterns);
    filedrop.setArguments(arguments);
    filedrop.setDomains(domains);
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
