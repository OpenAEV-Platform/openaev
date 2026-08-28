package io.openaev.service;

import static io.openaev.service.InjectExpectationService.COLLECTOR;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorCompositeId;
import io.openaev.database.model.InjectExpectationTrace;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.raw.impl.SimpleRawExpectationTrace;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.InjectExpectationRepository;
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
  private final InjectExpectationRepository injectExpectationRepository;
  private final ResultsMetricCollector resultsMetricCollector;

  public List<InjectExpectationTrace> getInjectExpectationTracesFromCollector(
      @NotNull String injectExpectationId, @NotNull String sourceId) {
    return deduplicateByAlertIdentity(
        this.injectExpectationTraceRepository.findByExpectationsAndSecurityPlatform(
            resolveAlertExpectationIds(injectExpectationId), sourceId));
  }

  public long getAlertLinksNumber(
      @NotNull String injectExpectationId,
      @NotNull String sourceId,
      String expectationResultSourceType) {
    String securityPlatformId;
    if (expectationResultSourceType.equalsIgnoreCase(COLLECTOR)) {
      securityPlatformId =
          securityPlatformRepository
              .findByExternalReference(sourceId)
              .orElseThrow(() -> new ElementNotFoundException("Security platform not found"))
              .getId();
    } else {
      securityPlatformId = sourceId;
    }
    return deduplicateByAlertIdentity(
            this.injectExpectationTraceRepository.findByExpectationsAndSecurityPlatform(
                resolveAlertExpectationIds(injectExpectationId), securityPlatformId))
        .size();
  }

  /**
   * A trace belongs to exactly one expectation, and the collector stores the same physical alert
   * once per matching agent expectation. When several expectations are rolled up onto the endpoint
   * row, that alert would therefore be listed and counted once per agent; keep a single
   * representative per (name, link) pair so the aggregated view reflects distinct alerts. The DB
   * query has no ORDER BY, so the traces are first sorted (newest alert first, id as the final
   * tie-break) to make the chosen representative - and the date it displays - deterministic.
   * Single-expectation lookups also go through here: the uniqueness constraint on (expectation,
   * source, name, link) treats NULLs as distinct, so duplicate rows remain possible when the alert
   * name or link is NULL.
   *
   * @param traces the traces returned for the resolved expectation ids
   * @return the distinct alerts, newest first, per-agent duplicates removed
   */
  private List<InjectExpectationTrace> deduplicateByAlertIdentity(
      final List<InjectExpectationTrace> traces) {
    List<InjectExpectationTrace> sortedTraces = new ArrayList<>(traces);
    sortedTraces.sort(
        Comparator.comparing(
                InjectExpectationTrace::getAlertDate,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(
                InjectExpectationTrace::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(
                InjectExpectationTrace::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    Set<AlertIdentity> seenAlerts = new HashSet<>();
    List<InjectExpectationTrace> distinctTraces = new ArrayList<>();
    for (InjectExpectationTrace trace : sortedTraces) {
      if (seenAlerts.add(new AlertIdentity(trace.getAlertName(), trace.getAlertLink()))) {
        distinctTraces.add(trace);
      }
    }
    return distinctTraces;
  }

  /**
   * Identity of a physical alert as reported by the security platform. A dedicated key type (not a
   * string concatenation) so values containing any separator, or null fields, can never make two
   * different alerts collide or the same alert count twice.
   *
   * @param name the alert name
   * @param link the alert link
   */
  private record AlertIdentity(String name, String link) {}

  /**
   * Alerts (traces) are attached by the collector to the AGENT-level expectations. The asset
   * (endpoint) target-results row shows the asset-level expectation, which carries none of its own,
   * so its alert count would always read 0. When the given expectation is an asset-level technical
   * expectation (asset set, no agent), we roll its alerts up from its child agent expectations of
   * the same type, plus the asset expectation itself (agentless endpoints and any result written
   * directly at the asset level). Any other expectation resolves to itself.
   *
   * @param injectExpectationId the expectation whose alerts are requested
   * @return the list of expectation ids whose traces must be aggregated
   */
  private List<String> resolveAlertExpectationIds(@NotNull final String injectExpectationId) {
    return injectExpectationRepository
        .findById(injectExpectationId)
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .filter(
            expectation ->
                expectation.getAsset() != null
                    && expectation.getAgent() == null
                    && expectation.getInject() != null)
        .map(
            assetExpectation -> {
              List<String> expectationIds = new ArrayList<>();
              expectationIds.add(injectExpectationId);
              injectExpectationRepository
                  .findAllWithAgentsByInjectAndAsset(
                      assetExpectation.getInject().getId(),
                      assetExpectation.getAsset().getId(),
                      assetExpectation.getType())
                  .stream()
                  .map(BaseInjectExpectation::getId)
                  .forEach(expectationIds::add);
              return expectationIds;
            })
        .orElseGet(() -> List.of(injectExpectationId));
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
