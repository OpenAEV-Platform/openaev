package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectationSignature;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.PreviewFeatureService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class SignatureOutputProcessorTest {

  private final InjectExpectationRepository injectExpectationRepository =
      mock(InjectExpectationRepository.class);
  private final CollectorService collectorService = mock(CollectorService.class);
  private final SecurityCoverageSendJobService securityCoverageSendJobService =
      mock(SecurityCoverageSendJobService.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<InjectExpectationService> selfProvider = mock(ObjectProvider.class);

  private final InjectExpectationService injectExpectationService =
      new InjectExpectationService(
          injectExpectationRepository, collectorService, securityCoverageSendJobService);
  private final PreviewFeatureService previewFeatureService = mock(PreviewFeatureService.class);
  private final SignatureOutputProcessor processor =
      new SignatureOutputProcessor(injectExpectationService, previewFeatureService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  SignatureOutputProcessorTest() {
    ReflectionTestUtils.setField(injectExpectationService, "selfProvider", selfProvider);
    when(selfProvider.getObject()).thenReturn(injectExpectationService);
  }

  @Test
  @DisplayName("Should expose Signature type")
  void shouldExposeSignatureType() {
    assertEquals(ContractOutputType.ExpectationSignature, processor.getType());
  }

  @Test
  @DisplayName("Should expose Object technical type")
  void shouldExposeObjectTechnicalType() {
    assertEquals(ContractOutputTechnicalType.Object, processor.getTechnicalType());
  }

  @Test
  @DisplayName("Should expose empty fields")
  void shouldExposeEmptyFields() {
    assertEquals(List.of(), processor.getFields());
  }

  @Test
  @DisplayName("Should validate non-null json node and reject null")
  void shouldValidateNonNullJsonNodeAndRejectNull() throws Exception {
    assertTrue(processor.validate(objectMapper.readTree("{}")));
    assertFalse(processor.validate(null));
  }

  @Test
  @DisplayName("Should skip processing when feature flag is disabled")
  void shouldSkipProcessingWhenFeatureFlagIsDisabled() throws Exception {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
        .thenReturn(false);

    ExecutionProcessingContext executionProcessingContext = mock(ExecutionProcessingContext.class);
    Inject inject = mock(Inject.class);
    when(inject.getId()).thenReturn("inject-1");
    when(executionProcessingContext.inject()).thenReturn(inject);

    ContractOutputContext contractOutputContext =
        new ContractOutputContext(
            "signatures",
            "signatures",
            ContractOutputType.ExpectationSignature,
            false,
            new String[0],
            new String[] {"SIGNATURES_PROCESSING"});

    assertDoesNotThrow(
        () ->
            processor.process(
                executionProcessingContext, contractOutputContext, objectMapper.readTree("{}")));

    verify(injectExpectationRepository, never())
        .findAllByInjectAndAgent(eq("inject-1"), eq("agent-1"));
  }

  @Test
  @DisplayName("First call clears signatures, second call appends without clearing")
  void shouldClearOnFirstCallAndAppendOnSecondCall() throws Exception {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
        .thenReturn(true);

    Inject inject = mock(Inject.class);
    when(inject.getId()).thenReturn("inject-1");

    ExecutionProcessingContext executionProcessingContext = mock(ExecutionProcessingContext.class);
    when(executionProcessingContext.inject()).thenReturn(inject);

    ContractOutputContext contractOutputContext =
        new ContractOutputContext(
            "signatures",
            "signatures",
            ContractOutputType.ExpectationSignature,
            false,
            new String[0],
            new String[] {"SIGNATURES_PROCESSING"});

    InjectExpectation firstCallExpectation = new InjectExpectation();
    firstCallExpectation.setId("exp-1");
    firstCallExpectation.setType(InjectExpectation.EXPECTATION_TYPE.DETECTION);
    firstCallExpectation.setSignaturesInitialized(false);

    InjectExpectation secondCallExpectation = new InjectExpectation();
    secondCallExpectation.setId("exp-1");
    secondCallExpectation.setType(InjectExpectation.EXPECTATION_TYPE.DETECTION);
    secondCallExpectation.setSignaturesInitialized(true);

    when(injectExpectationRepository.findAllByInjectAndAgent("inject-1", "agent-1"))
        .thenReturn(List.of(firstCallExpectation), List.of(secondCallExpectation));
    when(injectExpectationRepository.findById("exp-1"))
        .thenReturn(Optional.of(firstCallExpectation), Optional.of(secondCallExpectation));

    String firstPayload =
        """
        {
          "targets": [
            {
              "signature_target": {"agent_id": "agent-1"},
              "signature_values": [
                {
                  "expectation_type": "DETECTION",
                  "values": [
                    {"signature_type": "rule_name", "signature_value": "Sigma rule"}
                  ]
                }
              ]
            }
          ]
        }
        """;

    String secondPayload =
        """
        {
          "targets": [
            {
              "signature_target": {"agent_id": "agent-1"},
              "signature_values": [
                {
                  "expectation_type": "DETECTION",
                  "values": [
                    {"signature_type": "rule_name", "signature_value": "Second Sigma rule"}
                  ]
                }
              ]
            }
          ]
        }
        """;

    processor.process(
        executionProcessingContext, contractOutputContext, objectMapper.readTree(firstPayload));
    processor.process(
        executionProcessingContext, contractOutputContext, objectMapper.readTree(secondPayload));

    verify(injectExpectationRepository, times(1)).clearSignaturesAndMarkInitialized("exp-1");
    verify(injectExpectationRepository)
        .appendSignature("exp-1", "rule_name", "Sigma rule");
    verify(injectExpectationRepository)
        .appendSignature("exp-1", "rule_name", "Second Sigma rule");
  }

  @Test
  @DisplayName("Target with agent ID should resolve expectations by inject_id + agent_id")
  void shouldResolveAgentTargetByInjectAndAgent() throws Exception {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
        .thenReturn(true);

    Inject inject = mock(Inject.class);
    when(inject.getId()).thenReturn("inject-1");

    ExecutionProcessingContext executionProcessingContext = mock(ExecutionProcessingContext.class);
    when(executionProcessingContext.inject()).thenReturn(inject);

    ContractOutputContext contractOutputContext =
        new ContractOutputContext(
            "signatures",
            "signatures",
            ContractOutputType.ExpectationSignature,
            false,
            new String[0],
            new String[] {"SIGNATURES_PROCESSING"});

    InjectExpectation expectation = new InjectExpectation();
    expectation.setId("exp-agent");
    expectation.setType(InjectExpectation.EXPECTATION_TYPE.DETECTION);
    expectation.setSignaturesInitialized(false);

    when(injectExpectationRepository.findAllByInjectAndAgent("inject-1", "agent-1"))
        .thenReturn(List.of(expectation));
    when(injectExpectationRepository.findById("exp-agent")).thenReturn(Optional.of(expectation));

    String payload =
        """
        {
          "targets": [
            {
              "signature_target": {"agent_id": "agent-1"},
              "signature_values": [
                {
                  "expectation_type": "DETECTION",
                  "values": [{"signature_type": "rule_name", "signature_value": "Sigma rule"}]
                }
              ]
            }
          ]
        }
        """;

    processor.process(
        executionProcessingContext, contractOutputContext, objectMapper.readTree(payload));

    verify(injectExpectationRepository).findAllByInjectAndAgent("inject-1", "agent-1");
  }

  @Test
  @DisplayName("Target with asset ID should resolve expectations by inject_id + asset_id")
  void shouldResolveAssetTargetByInjectAndAsset() throws Exception {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
        .thenReturn(true);

    Inject inject = mock(Inject.class);
    when(inject.getId()).thenReturn("inject-1");

    ExecutionProcessingContext executionProcessingContext = mock(ExecutionProcessingContext.class);
    when(executionProcessingContext.inject()).thenReturn(inject);

    ContractOutputContext contractOutputContext =
        new ContractOutputContext(
            "signatures",
            "signatures",
            ContractOutputType.ExpectationSignature,
            false,
            new String[0],
            new String[] {"SIGNATURES_PROCESSING"});

    InjectExpectation expectation = new InjectExpectation();
    expectation.setId("exp-asset");
    expectation.setType(InjectExpectation.EXPECTATION_TYPE.PREVENTION);
    expectation.setSignaturesInitialized(false);

    when(injectExpectationRepository.findAllByInjectAndAsset("inject-1", "asset-1"))
        .thenReturn(List.of(expectation));
    when(injectExpectationRepository.findById("exp-asset")).thenReturn(Optional.of(expectation));

    String payload =
        """
        {
          "targets": [
            {
              "signature_target": {"asset_id": "asset-1"},
              "signature_values": [
                {
                  "expectation_type": "PREVENTION",
                  "values": [{"signature_type": "rule_name", "signature_value": "Prevent rule"}]
                }
              ]
            }
          ]
        }
        """;

    processor.process(
        executionProcessingContext, contractOutputContext, objectMapper.readTree(payload));

    verify(injectExpectationRepository).findAllByInjectAndAsset("inject-1", "asset-1");
  }

  @Test
  @DisplayName("Missing target in DB should not throw and should not update signatures")
  void shouldNotThrowWhenTargetIsMissingInDb() throws Exception {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
        .thenReturn(true);

    Inject inject = mock(Inject.class);
    when(inject.getId()).thenReturn("inject-1");

    ExecutionProcessingContext executionProcessingContext = mock(ExecutionProcessingContext.class);
    when(executionProcessingContext.inject()).thenReturn(inject);

    ContractOutputContext contractOutputContext =
        new ContractOutputContext(
            "signatures",
            "signatures",
            ContractOutputType.ExpectationSignature,
            false,
            new String[0],
            new String[] {"SIGNATURES_PROCESSING"});

    when(injectExpectationRepository.findAllByInjectAndAgent("inject-1", "agent-missing"))
        .thenReturn(List.of());

    String payload =
        """
        {
          "targets": [
            {
              "signature_target": {"agent_id": "agent-missing"},
              "signature_values": [
                {
                  "expectation_type": "DETECTION",
                  "values": [{"signature_type": "rule_name", "signature_value": "Sigma rule"}]
                }
              ]
            }
          ]
        }
        """;

    assertDoesNotThrow(
        () ->
            processor.process(
                executionProcessingContext, contractOutputContext, objectMapper.readTree(payload)));

    verify(injectExpectationRepository, never()).clearSignaturesAndMarkInitialized(eq("exp-1"));
    verify(injectExpectationRepository, never())
        .appendSignature(eq("exp-1"), eq("rule_name"), eq("Sigma rule"));
  }
}
