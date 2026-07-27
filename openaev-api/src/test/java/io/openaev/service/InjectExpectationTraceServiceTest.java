package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.BaseInjectExpectation;
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
  void createInjectExpectationTrace_WithNullTrace() {
    // Act & Assert
    injectExpectationTraceService.bulkInsertInjectExpectationTraces(List.of());
    verify(collectorRepository, never()).save(any());
    verify(injectExpectationTraceRepository, never()).save(any());
  }
}
