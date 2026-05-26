package io.openaev.rest.inject_expectation_trace;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.Collector;
import io.openaev.database.model.InjectExpectationTrace;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject_expectation_trace.form.InjectExpectationTraceBulkInsertInput;
import io.openaev.rest.inject_expectation_trace.form.InjectExpectationTraceInput;
import io.openaev.service.InjectExpectationTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping({
  InjectExpectationTraceApi.INJECT_EXPECTATION_TRACES_URI,
  InjectExpectationTraceApi.TENANT_INJECT_EXPECTATION_TRACES_URI
})
@Slf4j
public class InjectExpectationTraceApi extends RestBehavior {

  public static final String INJECT_EXPECTATION_TRACES_URI = "/api/inject-expectations-traces";
  public static final String TENANT_INJECT_EXPECTATION_TRACES_URI =
      TENANT_PREFIX + "/inject-expectations-traces";

  private final InjectExpectationTraceService injectExpectationTraceService;
  private final InjectExpectationTraceRepository injectExpectationTraceRepository;
  private final CollectorRepository collectorRepository;

  /**
   * @deprecated since 1.16.0, forRemoval = true
   * @see #bulkInsertInjectExpectationTraceForCollector(InjectExpectationTraceBulkInsertInput)
   */
  @Deprecated(since = "1.16.0", forRemoval = true)
  @Operation(
      summary =
          "Create inject expectation trace for collector. Deprecated since 1.16.0. Replaced by "
              + INJECT_EXPECTATION_TRACES_URI
              + "/bulk")
  @PostMapping()
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public InjectExpectationTrace createInjectExpectationTraceForCollector(
      @Valid @RequestBody InjectExpectationTraceInput input) {

    InjectExpectationTraceBulkInsertInput bulkInput = new InjectExpectationTraceBulkInsertInput();
    bulkInput.setExpectationTraces(List.of(input));

    this.bulkInsertInjectExpectationTraceForCollector(bulkInput);

    Collector collector =
        collectorRepository
            .findByIdAndTenantId(input.getSourceId(), TenantContext.getCurrentTenant())
            .orElseThrow(() -> new ElementNotFoundException("Collector not found"));

    return this.injectExpectationTraceRepository
        .findByAlertLinkAndAlertNameAndSecurityPlatformAndInjectExpectation(
            input.getAlertLink(),
            input.getAlertName(),
            collector.getSecurityPlatform().getId(),
            input.getInjectExpectationId());
  }

  /**
   * Bulk insert inject expectation traces for a collector.
   *
   * @param inputs the list of inject expectation trace inputs to be inserted
   */
  @Operation(summary = "Bulk insert inject expectation traces")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Inject expectation traces inserted successfully")
      })
  @LogExecutionTime
  @PostMapping("/bulk")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public void bulkInsertInjectExpectationTraceForCollector(
      @Valid @RequestBody @NotNull InjectExpectationTraceBulkInsertInput inputs) {
    if (inputs.getExpectationTraces().isEmpty()) {
      return;
    }
    this.injectExpectationTraceService.bulkInsertInjectExpectationTraces(
        inputs.getExpectationTraces());
  }

  @Operation(summary = "Get inject expectation traces from collector")
  @GetMapping()
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<InjectExpectationTrace> getInjectExpectationTracesFromCollector(
      @RequestParam String injectExpectationId, @RequestParam String sourceId) {
    try {
      Collector collector =
          collectorRepository
              .findByIdAndTenantId(sourceId, TenantContext.getCurrentTenant())
              .orElseThrow(() -> new ElementNotFoundException("Collector not found"));
      return this.injectExpectationTraceService.getInjectExpectationTracesFromCollector(
          injectExpectationId, collector.getSecurityPlatform().getId());
    } catch (ElementNotFoundException e) {
      return Collections.emptyList();
    }
  }

  @Operation(summary = "Get inject expectation traces' count")
  @GetMapping("/count")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public long getAlertLinksNumber(
      @RequestParam String injectExpectationId,
      @RequestParam String sourceId,
      @RequestParam String expectationResultSourceType) {
    return this.injectExpectationTraceService.getAlertLinksNumber(
        injectExpectationId, sourceId, expectationResultSourceType);
  }
}
