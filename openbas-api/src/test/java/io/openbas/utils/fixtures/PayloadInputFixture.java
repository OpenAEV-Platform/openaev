package io.openbas.utils.fixtures;

import static io.openbas.database.model.Payload.PAYLOAD_SOURCE.COMMUNITY;
import static io.openbas.database.model.Payload.PAYLOAD_STATUS.UNVERIFIED;
import static io.openbas.utils.fixtures.payload_fixture.ContractOutputElementInputFixture.createDefaultContractOutputElementInputCredentials;
import static io.openbas.utils.fixtures.payload_fixture.ContractOutputElementInputFixture.createDefaultContractOutputElementInputIPV6;
import static io.openbas.utils.fixtures.payload_fixture.OutputParserInputFixture.createDefaultOutputParseInput;
import static io.openbas.utils.fixtures.payload_fixture.RegexGroupInputFixture.createDefaultRegexGroupInputCredentials;
import static io.openbas.utils.fixtures.payload_fixture.RegexGroupInputFixture.createDefaultRegexGroupInputIPV6;

import io.openbas.database.model.*;
import io.openbas.rest.payload.contract_output_element.ContractOutputElementInput;
import io.openbas.rest.payload.form.*;
import io.openbas.rest.payload.output_parser.OutputParserInput;
import io.openbas.rest.payload.regex_group.RegexGroupInput;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class PayloadInputFixture {

  public static PayloadCreateInput createDefaultPayloadCreateInputForCommandLine() {
    PayloadCreateInput input = new PayloadCreateInput();
    input.setType(Command.COMMAND_TYPE);
    input.setName("Command line payload");
    input.setDescription("This does something, maybe");
    input.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    input.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    input.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});
    input.setAttackPatternsIds(Collections.emptyList());
    input.setTagIds(Collections.emptyList());
    input.setExecutor("bash");
    input.setContent("echo hello");
    return input;
  }

  public static PayloadCreateInput createDefaultPayloadCreateInputWithOutputParser() {
    PayloadCreateInput input = createDefaultPayloadCreateInputForCommandLine();

    RegexGroupInput regexGroupInput = createDefaultRegexGroupInputIPV6();

    ContractOutputElementInput contractOutputElementInput =
        createDefaultContractOutputElementInputIPV6();
    contractOutputElementInput.setRegexGroups(Set.of(regexGroupInput));

    OutputParserInput outputParserInput = createDefaultOutputParseInput();
    outputParserInput.setContractOutputElements(Set.of(contractOutputElementInput));

    input.setOutputParsers(Set.of(outputParserInput));
    return input;
  }

  public static PayloadCreateInput createDefaultPayloadCreateInputWithDetectionRemediation() {
    PayloadCreateInput input = createDefaultPayloadCreateInputForCommandLine();
    input.setDetectionRemediations(buildDetectionRemediations());
    return input;
  }

  @NotNull
  public static List<DetectionRemediationInput> buildDetectionRemediations() {
    DetectionRemediationInput drInputCS = new DetectionRemediationInput();
    drInputCS.setCollectorType("CS");
    drInputCS.setValues("Detection Remediation Gap for Crowdstrike");

    DetectionRemediationInput drInputSentinel = new DetectionRemediationInput();
    drInputSentinel.setCollectorType("SENTINEL");
    drInputSentinel.setValues("Detection Remediation Gap for Sentinel");

    DetectionRemediationInput srInputDefender = new DetectionRemediationInput();
    srInputDefender.setCollectorType("DEFENDER");
    srInputDefender.setValues("Detection Remediation Gap for Defender");
    return List.of(drInputCS, drInputSentinel, srInputDefender);
  }

  public static PayloadCreateInput createDefaultPayloadCreateInputForExecutable() {
    PayloadCreateInput input = new PayloadCreateInput();
    input.setType(Executable.EXECUTABLE_TYPE);
    input.setName("My Executable Payload");
    input.setDescription("Executable description");
    input.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    input.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    input.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});
    input.setAttackPatternsIds(Collections.emptyList());
    input.setTagIds(Collections.emptyList());
    input.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.x86_64);
    return input;
  }

  public static PayloadUpdateInput getDefaultExecutablePayloadUpdateInput() {
    PayloadUpdateInput updateInput = new PayloadUpdateInput();
    updateInput.setName("My Updated Executable Payload");
    updateInput.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.MacOS});
    updateInput.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.arm64);
    return updateInput;
  }

  public static PayloadUpdateInput getDefaultCommandPayloadUpdateInput() {
    PayloadUpdateInput input = new PayloadUpdateInput();
    input.setName("Updated Command line payload");
    input.setDescription("Command line description");
    input.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.MacOS});
    input.setTagIds(Collections.emptyList());
    input.setExecutor("sh");
    input.setContent("ufw prepend deny from 1.2.3.4\n" + "ufw status numbered\n");
    return input;
  }

  public static PayloadUpdateInput getDefaultCommandPayloadUpdateInputWithOutputParser() {
    PayloadUpdateInput input = getDefaultCommandPayloadUpdateInput();

    ContractOutputElementInput contractOutputElementInput =
        createDefaultContractOutputElementInputIPV6();

    OutputParserInput outputParserInput = createDefaultOutputParseInput();
    outputParserInput.setContractOutputElements(Set.of(contractOutputElementInput));

    input.setOutputParsers(Set.of(outputParserInput));
    return input;
  }

  public static PayloadUpdateInput getDefaultPayloadUpdateInputWithDetectionRemediation() {
    PayloadUpdateInput input = getDefaultCommandPayloadUpdateInput();
    input.setDetectionRemediations(buildDetectionRemediations());
    return input;
  }

  public static PayloadUpsertInput getDefaultCommandPayloadUpsertInput() {
    PayloadUpsertInput input = new PayloadUpsertInput();
    input.setType(Command.COMMAND_TYPE);
    input.setName("My Command Payload");
    input.setDescription("Command description");
    input.setContent("cd ..");
    input.setExecutor("PowerShell");
    input.setSource(COMMUNITY);
    input.setStatus(UNVERIFIED);
    input.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.MacOS});
    return input;
  }

  public static Document createDefaultExecutableFile() {
    Document executableFile = new Document();
    executableFile.setName("Executable file");
    executableFile.setType("text/x-sh");
    return executableFile;
  }

  public static PayloadUpsertInput getDefaultCommandPayloadUpsertInputWithOutputParser() {
    PayloadUpsertInput input = getDefaultCommandPayloadUpsertInput();

    ContractOutputElementInput contractOutputElementInput = getContractOutputElementInput();

    OutputParserInput outputParserInput = createDefaultOutputParseInput();
    outputParserInput.setContractOutputElements(Set.of(contractOutputElementInput));

    input.setOutputParsers(Set.of(outputParserInput));
    return input;
  }

  public static PayloadUpsertInput getDefaultCommandPayloadUpsertInputWithDetectionRemediations() {
    PayloadUpsertInput input = getDefaultCommandPayloadUpsertInput();
    input.setDetectionRemediations(buildDetectionRemediations());
    return input;
  }

  @NotNull
  private static ContractOutputElementInput getContractOutputElementInput() {
    RegexGroupInput regexGroupUserNameInput = createDefaultRegexGroupInputCredentials();

    ContractOutputElementInput contractOutputElementInput =
        createDefaultContractOutputElementInputCredentials();
    contractOutputElementInput.setRegexGroups(Set.of(regexGroupUserNameInput));
    return contractOutputElementInput;
  }
}
