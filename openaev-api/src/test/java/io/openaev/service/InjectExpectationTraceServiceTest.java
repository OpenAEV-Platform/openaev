package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationTrace;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExpectationTraceServiceTest {

  @Mock private InjectExpectationTraceRepository injectExpectationTraceRepository;
  @Mock private CollectorRepository collectorRepository;
  @Mock private InjectExpectationRepository injectExpectationRepository;

  @InjectMocks private InjectExpectationTraceService injectExpectationTraceService;

  private InjectExpectationTrace injectExpectationTrace;
  private SecurityPlatform securityPlatform;
  private String injectExpectationId;
  private String securityPlatformId;
  private String expectationResultSourceType;

  @BeforeEach
  void setUp() {
    injectExpectationId = UUID.randomUUID().toString();
    securityPlatformId = UUID.randomUUID().toString();
    expectationResultSourceType = "TYPE";

    BaseInjectExpectation baseInjectExpectation = new BaseInjectExpectation();
    baseInjectExpectation.setId(injectExpectationId);

    securityPlatform = new SecurityPlatform();
    securityPlatform.setId(securityPlatformId);

    injectExpectationTrace = new InjectExpectationTrace();
    injectExpectationTrace.setId(UUID.randomUUID().toString());
    injectExpectationTrace.setInjectExpectation(baseInjectExpectation);
    injectExpectationTrace.setSecurityPlatform(securityPlatform);
    injectExpectationTrace.setAlertDate(Instant.now());
    injectExpectationTrace.setAlertLink("http://test-link.com");
    injectExpectationTrace.setAlertName("Test Alert");
  }

  @Test
  void getInjectExpectationTracesFromCollector_Success() {
    // Arrange
    List<InjectExpectationTrace> expectedTraces = Collections.singletonList(injectExpectationTrace);
    when(injectExpectationRepository.findById(injectExpectationId)).thenReturn(Optional.empty());
    when(injectExpectationTraceRepository.findByExpectationsAndSecurityPlatform(
            anyCollection(), anyString()))
        .thenReturn(expectedTraces);

    // Act
    List<InjectExpectationTrace> result =
        injectExpectationTraceService.getInjectExpectationTracesFromCollector(
            injectExpectationId, securityPlatformId);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(injectExpectationTrace, result.get(0));
    verify(injectExpectationTraceRepository)
        .findByExpectationsAndSecurityPlatform(List.of(injectExpectationId), securityPlatformId);
  }

  @Test
  void getInjectExpectationTracesFromCollector_EmptyResult() {
    // Arrange
    when(injectExpectationRepository.findById(injectExpectationId)).thenReturn(Optional.empty());
    when(injectExpectationTraceRepository.findByExpectationsAndSecurityPlatform(
            anyCollection(), anyString()))
        .thenReturn(Collections.emptyList());

    // Act
    List<InjectExpectationTrace> result =
        injectExpectationTraceService.getInjectExpectationTracesFromCollector(
            injectExpectationId, securityPlatformId);

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
    verify(injectExpectationTraceRepository)
        .findByExpectationsAndSecurityPlatform(List.of(injectExpectationId), securityPlatformId);
  }

  @Test
  void getAlertLinksNumber_Success() {
    // Arrange
    long expectedCount = 5L;
    when(injectExpectationRepository.findById(injectExpectationId)).thenReturn(Optional.empty());
    when(injectExpectationTraceRepository.countAlertsForExpectations(anyCollection(), anyString()))
        .thenReturn(expectedCount);

    // Act
    long result =
        injectExpectationTraceService.getAlertLinksNumber(
            injectExpectationId, securityPlatformId, expectationResultSourceType);

    // Assert
    assertEquals(expectedCount, result);
    verify(injectExpectationTraceRepository)
        .countAlertsForExpectations(List.of(injectExpectationId), securityPlatformId);
  }

  @Test
  void getAlertLinksNumber_ZeroCount() {
    // Arrange
    when(injectExpectationRepository.findById(injectExpectationId)).thenReturn(Optional.empty());
    when(injectExpectationTraceRepository.countAlertsForExpectations(anyCollection(), anyString()))
        .thenReturn(0L);

    // Act
    long result =
        injectExpectationTraceService.getAlertLinksNumber(
            injectExpectationId, securityPlatformId, expectationResultSourceType);

    // Assert
    assertEquals(0L, result);
    verify(injectExpectationTraceRepository)
        .countAlertsForExpectations(List.of(injectExpectationId), securityPlatformId);
  }

  @Test
  void getAlertLinksNumber_AssetLevelExpectation_AggregatesChildAgentExpectations() {
    // Arrange: an asset-level detection expectation (asset set, no agent) whose alerts live on
    // its two child agent expectations of the same type
    DetectionInjectExpectation assetExpectation = buildAssetLevelExpectation(injectExpectationId);
    when(injectExpectationRepository.findById(injectExpectationId))
        .thenReturn(Optional.of(assetExpectation));
    when(injectExpectationRepository.findAllWithAgentsByInjectAndAsset(
            assetExpectation.getInject().getId(),
            assetExpectation.getAsset().getId(),
            assetExpectation.getType()))
        .thenReturn(
            List.of(
                buildAgentLevelExpectation("agent-expectation-1"),
                buildAgentLevelExpectation("agent-expectation-2")));
    when(injectExpectationTraceRepository.countAlertsForExpectations(anyCollection(), anyString()))
        .thenReturn(3L);

    // Act
    long result =
        injectExpectationTraceService.getAlertLinksNumber(
            injectExpectationId, securityPlatformId, expectationResultSourceType);

    // Assert: the count query receives the asset expectation id plus both child agent ids
    assertEquals(3L, result);
    verify(injectExpectationTraceRepository)
        .countAlertsForExpectations(
            List.of(injectExpectationId, "agent-expectation-1", "agent-expectation-2"),
            securityPlatformId);
  }

  @Test
  void getInjectExpectationTracesFromCollector_AssetLevelExpectation_AggregatesChildAgents() {
    // Arrange
    DetectionInjectExpectation assetExpectation = buildAssetLevelExpectation(injectExpectationId);
    when(injectExpectationRepository.findById(injectExpectationId))
        .thenReturn(Optional.of(assetExpectation));
    when(injectExpectationRepository.findAllWithAgentsByInjectAndAsset(
            assetExpectation.getInject().getId(),
            assetExpectation.getAsset().getId(),
            assetExpectation.getType()))
        .thenReturn(List.of(buildAgentLevelExpectation("agent-expectation-1")));
    when(injectExpectationTraceRepository.findByExpectationsAndSecurityPlatform(
            anyCollection(), anyString()))
        .thenReturn(List.of(injectExpectationTrace));

    // Act
    List<InjectExpectationTrace> result =
        injectExpectationTraceService.getInjectExpectationTracesFromCollector(
            injectExpectationId, securityPlatformId);

    // Assert: the list query receives the asset expectation id plus the child agent id
    assertEquals(1, result.size());
    verify(injectExpectationTraceRepository)
        .findByExpectationsAndSecurityPlatform(
            List.of(injectExpectationId, "agent-expectation-1"), securityPlatformId);
  }

  @Test
  void getAlertLinksNumber_AgentLevelExpectation_ResolvesToItself() {
    // Arrange: an agent-level expectation must never trigger the aggregation lookup
    DetectionInjectExpectation agentExpectation = buildAgentLevelExpectation(injectExpectationId);
    when(injectExpectationRepository.findById(injectExpectationId))
        .thenReturn(Optional.of(agentExpectation));
    when(injectExpectationTraceRepository.countAlertsForExpectations(anyCollection(), anyString()))
        .thenReturn(1L);

    // Act
    long result =
        injectExpectationTraceService.getAlertLinksNumber(
            injectExpectationId, securityPlatformId, expectationResultSourceType);

    // Assert
    assertEquals(1L, result);
    verify(injectExpectationTraceRepository)
        .countAlertsForExpectations(List.of(injectExpectationId), securityPlatformId);
    verify(injectExpectationRepository, never())
        .findAllWithAgentsByInjectAndAsset(anyString(), anyString(), any());
  }

  private DetectionInjectExpectation buildAssetLevelExpectation(final String expectationId) {
    Inject inject = new Inject();
    inject.setId(UUID.randomUUID().toString());
    Asset asset = new Asset();
    asset.setId(UUID.randomUUID().toString());
    DetectionInjectExpectation assetExpectation = new DetectionInjectExpectation();
    assetExpectation.setId(expectationId);
    assetExpectation.setInject(inject);
    assetExpectation.setAsset(asset);
    return assetExpectation;
  }

  private DetectionInjectExpectation buildAgentLevelExpectation(final String expectationId) {
    Agent agent = new Agent();
    agent.setId(UUID.randomUUID().toString());
    DetectionInjectExpectation agentExpectation = new DetectionInjectExpectation();
    agentExpectation.setId(expectationId);
    agentExpectation.setAgent(agent);
    return agentExpectation;
  }

  @Test
  void createInjectExpectationTrace_WithNullTrace() {
    // Act & Assert
    injectExpectationTraceService.bulkInsertInjectExpectationTraces(List.of());
    verify(collectorRepository, never()).save(any());
    verify(injectExpectationTraceRepository, never()).save(any());
  }
}
