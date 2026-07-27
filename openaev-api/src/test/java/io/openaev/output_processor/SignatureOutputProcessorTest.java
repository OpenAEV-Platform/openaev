package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.*;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SignatureOutputProcessorTest {

  private final InjectExpectationRepository injectExpectationRepository =
      mock(InjectExpectationRepository.class);
  private final CollectorService collectorService = mock(CollectorService.class);
  private final SecurityPlatformRepository securityPlatformRepository =
      mock(SecurityPlatformRepository.class);
  private final SecurityCoverageSendJobService securityCoverageSendJobService =
      mock(SecurityCoverageSendJobService.class);
  private final AssetGroupService assetGroupService = mock(AssetGroupService.class);
  private final InjectService injectService = mock(InjectService.class);
  private final InjectorContractContentUtils injectorContractContentUtils =
      mock(InjectorContractContentUtils.class);

  private final InjectExpectationLockService injectExpectationLockService =
      new InjectExpectationLockService(injectExpectationRepository);

  private final InjectExpectationService injectExpectationService =
      new InjectExpectationService(
          injectExpectationRepository,
          collectorService,
          securityPlatformRepository,
          securityCoverageSendJobService,
          injectExpectationLockService,
          assetGroupService,
          injectService,
          injectorContractContentUtils,
          new ArrayList<>(List.of()));
  private final PreviewFeatureService previewFeatureService = mock(PreviewFeatureService.class);
  private final SignatureOutputProcessor processor =
      new SignatureOutputProcessor(injectExpectationService, previewFeatureService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  SignatureOutputProcessorTest() {
    ReflectionTestUtils.setField(injectExpectationService, "mapper", objectMapper);
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

    DetectionInjectExpectation firstCallExpectation = new DetectionInjectExpectation();
    firstCallExpectation.setId("exp-1");
    firstCallExpectation.setSignaturesInitialized(false);

    DetectionInjectExpectation secondCallExpectation = new DetectionInjectExpectation();
    secondCallExpectation.setId("exp-1");
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

    verify(injectExpectationRepository, times(2)).save(any());
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

    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setId("exp-agent");
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

    PreventionInjectExpectation expectation = new PreventionInjectExpectation();
    expectation.setId("exp-asset");
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

    verify(injectExpectationRepository, never()).save(any());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private ExecutionProcessingContext buildCtx(String injectId) {
    Inject inject = mock(Inject.class);
    when(inject.getId()).thenReturn(injectId);
    ExecutionProcessingContext ctx = mock(ExecutionProcessingContext.class);
    when(ctx.inject()).thenReturn(inject);
    return ctx;
  }

  private ContractOutputContext buildContractCtx() {
    return new ContractOutputContext(
        "signatures",
        "signatures",
        ContractOutputType.ExpectationSignature,
        false,
        new String[0],
        new String[] {"SIGNATURES_PROCESSING"});
  }

  // -------------------------------------------------------------------------
  // Branch coverage — process(): early returns and continues
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("process() — structural branches")
  class ProcessStructuralBranches {

    @Test
    @DisplayName("given targets node not an array should return early without processing")
    void given_targetsNotArray_should_returnEarly() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree("{\"targets\": \"bad\"}"));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
      verify(injectExpectationRepository, never()).findAllByInjectAndAsset(any(), any());
    }

    @Test
    @DisplayName("given signature_values node not an array should skip that target and continue")
    void given_signatureValuesNotArray_should_skipTarget() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": "not-an-array"
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
    }
  }

  // -------------------------------------------------------------------------
  // Branch coverage — mapExpectationType()
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("mapExpectationType() — branches")
  class MapExpectationTypeBranches {

    @Test
    @DisplayName("given expectation_type missing (null text) should skip without processing")
    void given_expectationTypeNull_should_skip() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": [
                  {
                    "values": [{"signature_type": "rule_name", "signature_value": "Sigma"}]
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
    }

    @Test
    @DisplayName("given expectation_type is MANUAL (not DETECTION/PREVENTION) should skip")
    void given_expectationTypeManual_should_skip() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": [
                  {
                    "expectation_type": "MANUAL",
                    "values": [{"signature_type": "rule_name", "signature_value": "Sigma"}]
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
    }

    @Test
    @DisplayName(
        "given expectation_type is an unknown string (IllegalArgumentException) should skip")
    void given_expectationTypeUnknownString_should_skip() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": [
                  {
                    "expectation_type": "NOT_VALID_ENUM_VALUE",
                    "values": [{"signature_type": "rule_name", "signature_value": "Sigma"}]
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
    }
  }

  // -------------------------------------------------------------------------
  // Branch coverage — extractSignatures()
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("extractSignatures() — branches")
  class ExtractSignaturesBranches {

    @Test
    @DisplayName("given values node not an array should produce empty list and skip processing")
    void given_valuesNotArray_should_skipProcessing() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": [
                  {
                    "expectation_type": "DETECTION",
                    "values": "not-an-array"
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
    }

    @Test
    @DisplayName("given values array is empty should skip processing")
    void given_valuesArrayEmpty_should_skipProcessing() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": [
                  {
                    "expectation_type": "DETECTION",
                    "values": []
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
    }

    @Test
    @DisplayName("given signature with empty type should be filtered out and skip processing")
    void given_signatureWithEmptyType_should_skipProcessing() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": [
                  {
                    "expectation_type": "DETECTION",
                    "values": [{"signature_type": "", "signature_value": "Sigma rule"}]
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
    }

    @Test
    @DisplayName("given signature with empty value should be filtered out and skip processing")
    void given_signatureWithEmptyValue_should_skipProcessing() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": [
                  {
                    "expectation_type": "DETECTION",
                    "values": [{"signature_type": "rule_name", "signature_value": ""}]
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
    }

    @Test
    @DisplayName("given signature using fallback 'type'/'value' keys should be parsed correctly")
    void given_signatureWithFallbackTypeValueKeys_should_processSignature() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-1");

      DetectionInjectExpectation expectation = new DetectionInjectExpectation();
      expectation.setId("exp-fallback");
      expectation.setSignaturesInitialized(false);

      when(injectExpectationRepository.findAllByInjectAndAgent("inject-1", "agent-1"))
          .thenReturn(List.of(expectation));
      when(injectExpectationRepository.findById("exp-fallback"))
          .thenReturn(Optional.of(expectation));

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent_id": "agent-1"},
                "signature_values": [
                  {
                    "expectation_type": "DETECTION",
                    "values": [{"type": "rule_name", "value": "Sigma rule via fallback"}]
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --

      verify(injectExpectationRepository).save(expectation);
    }
  }

  // -------------------------------------------------------------------------
  // Branch coverage — assetGroupId target resolution
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("process() — assetGroupId target resolution")
  class AssetGroupTargetResolution {

    @Test
    @DisplayName(
        "given asset_group_id in signature_target should resolve via findAllByInjectAndAssetGroup")
    void given_assetGroupId_should_resolveViaAssetGroup() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-1");

      DetectionInjectExpectation expectation = new DetectionInjectExpectation();
      expectation.setId("exp-group");
      expectation.setSignaturesInitialized(false);

      when(injectExpectationRepository.findAllByInjectAndAssetGroup("inject-1", "group-1"))
          .thenReturn(List.of(expectation));
      when(injectExpectationRepository.findById("exp-group")).thenReturn(Optional.of(expectation));

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"asset_group_id": "group-1"},
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

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository).findAllByInjectAndAssetGroup("inject-1", "group-1");
      verify(injectExpectationRepository).save(expectation);
    }

    @Test
    @DisplayName(
        "given fallback 'asset_group' key in signature_target should resolve via findAllByInjectAndAssetGroup")
    void given_assetGroupFallbackKey_should_resolveViaAssetGroup() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-1");

      DetectionInjectExpectation expectation = new DetectionInjectExpectation();
      expectation.setId("exp-group-fallback");
      expectation.setSignaturesInitialized(false);

      when(injectExpectationRepository.findAllByInjectAndAssetGroup("inject-1", "group-2"))
          .thenReturn(List.of(expectation));
      when(injectExpectationRepository.findById("exp-group-fallback"))
          .thenReturn(Optional.of(expectation));

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"asset_group": "group-2"},
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

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository).findAllByInjectAndAssetGroup("inject-1", "group-2");
    }
  }

  // -------------------------------------------------------------------------
  // Branch coverage — readText() fallback keys for agent/asset
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("readText() — fallback key resolution")
  class ReadTextFallbackKeys {

    @Test
    @DisplayName("given fallback 'agent' key in signature_target should resolve agent expectations")
    void given_agentFallbackKey_should_resolveAgentExpectations() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-1");

      DetectionInjectExpectation expectation = new DetectionInjectExpectation();
      expectation.setId("exp-agent-fb");
      expectation.setSignaturesInitialized(false);

      when(injectExpectationRepository.findAllByInjectAndAgent("inject-1", "agent-fallback"))
          .thenReturn(List.of(expectation));
      when(injectExpectationRepository.findById("exp-agent-fb"))
          .thenReturn(Optional.of(expectation));

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"agent": "agent-fallback"},
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

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository).findAllByInjectAndAgent("inject-1", "agent-fallback");
    }

    @Test
    @DisplayName("given fallback 'asset' key in signature_target should resolve asset expectations")
    void given_assetFallbackKey_should_resolveAssetExpectations() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-1");

      PreventionInjectExpectation expectation = new PreventionInjectExpectation();
      expectation.setId("exp-asset-fb");
      expectation.setSignaturesInitialized(false);

      when(injectExpectationRepository.findAllByInjectAndAsset("inject-1", "asset-fallback"))
          .thenReturn(List.of(expectation));
      when(injectExpectationRepository.findById("exp-asset-fb"))
          .thenReturn(Optional.of(expectation));

      String payload =
          """
          {
            "targets": [
              {
                "signature_target": {"asset": "asset-fallback"},
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

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert --
      verify(injectExpectationRepository).findAllByInjectAndAsset("inject-1", "asset-fallback");
    }

    @Test
    @DisplayName(
        "given signature_target is missing (null/missing node) should skip without processing")
    void given_missingSignatureTarget_should_skip() throws Exception {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR))
          .thenReturn(true);
      ExecutionProcessingContext ctx = buildCtx("inject-x");

      // No signature_target key → readText receives a MissingNode
      String payload =
          """
          {
            "targets": [
              {
                "signature_values": [
                  {
                    "expectation_type": "DETECTION",
                    "values": [{"signature_type": "rule_name", "signature_value": "Sigma"}]
                  }
                ]
              }
            ]
          }
          """;

      // -- Act --
      processor.process(ctx, buildContractCtx(), objectMapper.readTree(payload));

      // -- Assert -- (no agent/asset/assetGroup id → appendExpectationSignatures receives
      // all null)
      verify(injectExpectationRepository, never()).findAllByInjectAndAgent(any(), any());
      verify(injectExpectationRepository, never()).findAllByInjectAndAsset(any(), any());
      verify(injectExpectationRepository, never()).findAllByInjectAndAssetGroup(any(), any());
    }
  }
}
