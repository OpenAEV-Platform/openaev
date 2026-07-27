package io.openaev.service;

import static io.openaev.service.InjectExpectationService.COLLECTOR;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorCompositeId;
import io.openaev.database.model.InjectExpectationTrace;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.raw.impl.SimpleRawExpectationTrace;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject_expectation_trace.form.InjectExpectationTraceInput;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class InjectExpectationTraceService {

  private final InjectExpectationTraceRepository injectExpectationTraceRepository;
  private final SecurityPlatformRepository securityPlatformRepository;
  private final CollectorRepository collectorRepository;
  private final ResultsMetricCollector resultsMetricCollector;

  public List<InjectExpectationTrace> getInjectExpectationTracesFromCollector(
      @NotNull String injectExpectationId, @NotNull String sourceId) {
    return this.injectExpectationTraceRepository.findByExpectationAndSecurityPlatform(
        injectExpectationId, sourceId);
  }

  public long getAlertLinksNumber(
      @NotNull String injectExpectationId,
      @NotNull String sourceId,
      String expectationResultSourceType) {
    if (expectationResultSourceType.equalsIgnoreCase(COLLECTOR)) {
      SecurityPlatform securityPlatform =
          securityPlatformRepository
              .findByExternalReference(sourceId)
              .orElseThrow(() -> new ElementNotFoundException("Security platform not found"));
      return this.injectExpectationTraceRepository.countAlerts(
          injectExpectationId, securityPlatform.getId());
    } else {
      return this.injectExpectationTraceRepository.countAlerts(injectExpectationId, sourceId);
    }
  }

  @Transactional(rollbackFor = Exception.class)
  public void bulkInsertInjectExpectationTraces(
      @NotNull List<InjectExpectationTraceInput> injectExpectationTraces,
      @NotNull String tenantId) {
    if (injectExpectationTraces.isEmpty()) {
      return;
    }
    // We start by deduplicating the data, to avoid duplicates in the database
    // Convert the input list to InjectExpectationTrace objects and extract oldest trace's date
    // Start by getting the collector. We can take the first one since they are all the same
    Collector collector =
        collectorRepository
            .findById(
                ConnectorCompositeId.of(injectExpectationTraces.getFirst().getSourceId(), tenantId))
            .orElseThrow(() -> new ElementNotFoundException("Collector not found"));
    // Telemetry: expectation validation traces pushed by this collector - the
    // key prevention/detection value signal for EDR/SIEM integrations.
    resultsMetricCollector.recordExpectationValidations(
        collector.getType(), injectExpectationTraces.size());
    Map<SimpleRawExpectationTrace, InjectExpectationTrace> traces = new HashMap<>();
    injectExpectationTraces.forEach(
        input -> {
          // Convert input to InjectExpectationTrace
          InjectExpectationTrace trace = new InjectExpectationTrace();
          trace.setUpdateAttributes(input);
          trace.setSecurityPlatform(collector.getSecurityPlatform());
          // We don't need to fetch the actual expectation here, we can just set the id as there is
          // no cascade
          trace.setInjectExpectation(new BaseInjectExpectation());
          trace.getInjectExpectation().setId(input.getInjectExpectationId());

          SimpleRawExpectationTrace simpleTrace = SimpleRawExpectationTrace.of(trace);

          traces.computeIfAbsent(simpleTrace, k -> trace);
        });

    // Save the remaining traces
    for (InjectExpectationTrace trace : traces.values()) {
      this.injectExpectationTraceRepository.insertIfNotExists(
          UUID.randomUUID().toString(),
          trace.getInjectExpectation().getId(),
          trace.getSecurityPlatform().getId(),
          trace.getAlertLink(),
          trace.getAlertName(),
          trace.getAlertDate(),
          trace.getCreatedAt(),
          trace.getUpdatedAt());
    }
  }
}
