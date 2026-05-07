package io.openaev.api.threat_arsenal.dto;

import io.openaev.database.model.Endpoint;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadPrerequisite;
import io.openaev.rest.payload.form.DetectionRemediationInput;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import java.util.List;
import java.util.Set;

/**
 * Sealed interface exposing the fields shared by {@link ThreatArsenalActionCreateInput} and {@link
 * ThreatArsenalActionUpdateInput}.
 *
 * <p>Used to factor out the common mapping logic when converting action inputs to payload inputs.
 */
public sealed interface CommonActionInput
    permits ThreatArsenalActionCreateInput, ThreatArsenalActionUpdateInput {
  String name();

  Endpoint.PLATFORM_TYPE[] platforms();

  String description();

  String executor();

  String content();

  Payload.PAYLOAD_EXECUTION_ARCH executionArch();

  InjectExpectation.EXPECTATION_TYPE[] expectations();

  String executableFile();

  String fileDropFile();

  String hostname();

  List<PayloadArgument> arguments();

  List<PayloadPrerequisite> prerequisites();

  String cleanupExecutor();

  String cleanupCommand();

  List<String> tagIds();

  List<String> attackPatternsIds();

  List<DetectionRemediationInput> detectionRemediations();

  Set<OutputParserInput> outputParsers();

  List<String> domainIds();
}
