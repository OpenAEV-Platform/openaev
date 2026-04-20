package io.openaev.utils.fixtures;

import static io.openaev.utils.fixtures.payload_fixture.ContractOutputElementInputFixture.createDefaultContractOutputElementInputIPV6;
import static io.openaev.utils.fixtures.payload_fixture.OutputParserInputFixture.createDefaultOutputParseInput;
import static io.openaev.utils.fixtures.payload_fixture.RegexGroupInputFixture.createDefaultRegexGroupInputIPV6;

import io.openaev.database.model.*;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Payload.PAYLOAD_EXECUTION_ARCH;
import io.openaev.database.model.Payload.PAYLOAD_SOURCE;
import io.openaev.database.model.Payload.PAYLOAD_STATUS;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementInput;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.openaev.rest.payload.regex_group.RegexGroupInput;
import io.openaev.rest.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ThreatArsenalInputFixture {

  public static ThreatArsenalActionCreateInput createDefaultCommandLineAction(
      List<String> domainIds) {
    return new ThreatArsenalActionCreateInput(
        Command.COMMAND_TYPE,
        "Command line payload",
        PAYLOAD_SOURCE.MANUAL,
        PAYLOAD_STATUS.VERIFIED,
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
        new EXPECTATION_TYPE[] {},
        "This does something, maybe",
        "bash",
        "echo hello",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        domainIds);
  }

  public static ThreatArsenalActionCreateInput createDefaultExecutableAction(
      List<String> domainIds, String executableFileId) {
    return new ThreatArsenalActionCreateInput(
        Executable.EXECUTABLE_TYPE,
        "My Executable Payload",
        PAYLOAD_SOURCE.MANUAL,
        PAYLOAD_STATUS.VERIFIED,
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        PAYLOAD_EXECUTION_ARCH.x86_64,
        new EXPECTATION_TYPE[] {},
        "Executable description",
        null,
        null,
        executableFileId,
        null,
        null,
        null,
        null,
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        domainIds);
  }

  public static ThreatArsenalActionCreateInput createCommandLineActionWithOutputParser(
      List<String> domainIds) {
    RegexGroupInput regexGroupInput = createDefaultRegexGroupInputIPV6();
    ContractOutputElementInput contractOutputElementInput =
        createDefaultContractOutputElementInputIPV6();
    contractOutputElementInput.setRegexGroups(Set.of(regexGroupInput));
    OutputParserInput outputParserInput = createDefaultOutputParseInput();
    outputParserInput.setContractOutputElements(Set.of(contractOutputElementInput));

    return new ThreatArsenalActionCreateInput(
        Command.COMMAND_TYPE,
        "Command line payload",
        PAYLOAD_SOURCE.MANUAL,
        PAYLOAD_STATUS.VERIFIED,
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
        new EXPECTATION_TYPE[] {},
        "This does something, maybe",
        "bash",
        "echo hello",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        Set.of(outputParserInput),
        domainIds);
  }

  public static ThreatArsenalActionCreateInput createCommandLineActionWithDetectionRemediation(
      List<String> domainIds) {
    return new ThreatArsenalActionCreateInput(
        Command.COMMAND_TYPE,
        "Command line payload",
        PAYLOAD_SOURCE.MANUAL,
        PAYLOAD_STATUS.VERIFIED,
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
        new EXPECTATION_TYPE[] {},
        "This does something, maybe",
        "bash",
        "echo hello",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        PayloadInputFixture.buildDetectionRemediations(),
        null,
        domainIds);
  }

  public static ThreatArsenalActionCreateInput createCommandLineActionWithCleanup(
      List<String> domainIds, String cleanupExecutor, String cleanupCommand) {
    return new ThreatArsenalActionCreateInput(
        Command.COMMAND_TYPE,
        "Command line payload",
        PAYLOAD_SOURCE.MANUAL,
        PAYLOAD_STATUS.VERIFIED,
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
        new EXPECTATION_TYPE[] {},
        "This does something, maybe",
        "bash",
        "echo hello",
        null,
        null,
        null,
        null,
        null,
        cleanupExecutor,
        cleanupCommand,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        domainIds);
  }

  public static ThreatArsenalActionCreateInput createCommandLineActionWithTargetedAsset(
      List<String> domainIds) {
    PayloadArgument targetedAssetArgument = new PayloadArgument();
    targetedAssetArgument.setKey("URL");
    targetedAssetArgument.setType(ArgumentType.TargetedAsset);
    targetedAssetArgument.setDefaultValue("hostname");
    targetedAssetArgument.setSeparator("-u");

    return new ThreatArsenalActionCreateInput(
        Command.COMMAND_TYPE,
        "Command line payload",
        PAYLOAD_SOURCE.MANUAL,
        PAYLOAD_STATUS.VERIFIED,
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
        new EXPECTATION_TYPE[] {},
        "This does something, maybe",
        "bash",
        "echo hello",
        null,
        null,
        null,
        List.of(targetedAssetArgument),
        null,
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        domainIds);
  }
}
