package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectationTrace;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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

  @InjectMocks private InjectExpectationTraceService injectExpectationTraceService;

  private InjectExpectationTrace injectExpectationTrace;
  private InjectExpectation injectExpectation;
  private SecurityPlatform securityPlatform;
  private String injectExpectationId;
  private String securityPlatformId;
  private String expectationResultSourceType;

  @BeforeEach
  void setUp() {
    injectExpectationId = UUID.randomUUID().toString();
    securityPlatformId = UUID.randomUUID().toString();
    expectationResultSourceType = "TYPE";

    injectExpectation = new InjectExpectation();
    injectExpectation.setId(injectExpectationId);

    securityPlatform = new SecurityPlatform();
    securityPlatform.setId(securityPlatformId);

    injectExpectationTrace = new InjectExpectationTrace();
    injectExpectationTrace.setId(UUID.randomUUID().toString());
    injectExpectationTrace.setInjectExpectation(injectExpectation);
    injectExpectationTrace.setSecurityPlatform(securityPlatform);
    injectExpectationTrace.setAlertDate(Instant.now());
    injectExpectationTrace.setAlertLink("http://test-link.com");
    injectExpectationTrace.setAlertName("Test Alert");
  }

  @Test
  void getInjectExpectationTracesFromCollector_Success() {
    // Arrange
    List<InjectExpectationTrace> expectedTraces = Collections.singletonList(injectExpectationTrace);
    when(injectExpectationTraceRepository.findByExpectationAndSecurityPlatform(
            anyString(), anyString()))
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
        .findByExpectationAndSecurityPlatform(injectExpectationId, securityPlatformId);
  }

  @Test
  void getInjectExpectationTracesFromCollector_EmptyResult() {
    // Arrange
    when(injectExpectationTraceRepository.findByExpectationAndSecurityPlatform(
            anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    // Act
    List<InjectExpectationTrace> result =
        injectExpectationTraceService.getInjectExpectationTracesFromCollector(
            injectExpectationId, securityPlatformId);

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
    verify(injectExpectationTraceRepository)
        .findByExpectationAndSecurityPlatform(injectExpectationId, securityPlatformId);
  }

  @Test
  void getAlertLinksNumber_Success() {
    // Arrange
    long expectedCount = 5L;
    when(injectExpectationTraceRepository.countAlerts(anyString(), anyString()))
        .thenReturn(expectedCount);

    // Act
    long result =
        injectExpectationTraceService.getAlertLinksNumber(
            injectExpectationId, securityPlatformId, expectationResultSourceType);

    // Assert
    assertEquals(expectedCount, result);
    verify(injectExpectationTraceRepository).countAlerts(injectExpectationId, securityPlatformId);
  }

  @Test
  void getAlertLinksNumber_ZeroCount() {
    // Arrange
    when(injectExpectationTraceRepository.countAlerts(anyString(), anyString())).thenReturn(0L);

    // Act
    long result =
        injectExpectationTraceService.getAlertLinksNumber(
            injectExpectationId, securityPlatformId, expectationResultSourceType);

    // Assert
    assertEquals(0L, result);
    verify(injectExpectationTraceRepository).countAlerts(injectExpectationId, securityPlatformId);
  }

  @Test
  void createInjectExpectationTrace_WithNullTrace() {
    // Act & Assert
    injectExpectationTraceService.bulkInsertInjectExpectationTraces(List.of());
    verify(collectorRepository, never()).save(any());
    verify(injectExpectationTraceRepository, never()).save(any());
  }
}
