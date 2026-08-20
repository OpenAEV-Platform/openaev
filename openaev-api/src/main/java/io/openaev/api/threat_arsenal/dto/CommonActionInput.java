package io.openaev.api.threat_arsenal.dto;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadPrerequisite;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.rest.payload.form.DetectionRemediationInput;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import java.util.List;
import java.util.Map;
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

  BaseInjectExpectation.EXPECTATION_TYPE[] expectations();

  /**
   * Optional map of technical expectation type to the security platform types expected to fulfil it
   * (e.g. {@code {"DETECTION": ["EDR","XDR"], "PREVENTION": ["EDR"]}}). Empty or absent for a given
   * type means "any security platform" (legacy behaviour).
   */
  Map<BaseInjectExpectation.EXPECTATION_TYPE, List<SecurityPlatform.SECURITY_PLATFORM_TYPE>>
      expectedSecurityPlatforms();

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
