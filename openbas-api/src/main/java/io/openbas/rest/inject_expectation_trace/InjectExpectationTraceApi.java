package io.openbas.rest.inject_expectation_trace;

import io.openbas.aop.LogExecutionTime;
import io.openbas.database.model.Collector;
import io.openbas.database.model.InjectExpectationTrace;
import io.openbas.database.repository.CollectorRepository;
import io.openbas.database.repository.InjectExpectationTraceRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.inject_expectation_trace.form.InjectExpectationTraceBulkInsertInput;
import io.openbas.rest.inject_expectation_trace.form.InjectExpectationTraceInput;
import io.openbas.service.InjectExpectationTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping(InjectExpectationTraceApi.INJECT_EXPECTATION_TRACES_URI)
@PreAuthorize("isAdmin()")
@Slf4j
public class InjectExpectationTraceApi extends RestBehavior {

  public static final String INJECT_EXPECTATION_TRACES_URI = "/api/inject-expectations-traces";

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
  public InjectExpectationTrace createInjectExpectationTraceForCollector(
      @Valid @RequestBody InjectExpectationTraceInput input) {

    InjectExpectationTraceBulkInsertInput bulkInput = new InjectExpectationTraceBulkInsertInput();
    bulkInput.setExpectationTraces(List.of(input));

    this.bulkInsertInjectExpectationTraceForCollector(bulkInput);

    Collector collector =
        collectorRepository
            .findById(input.getSourceId())
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
  public List<InjectExpectationTrace> getInjectExpectationTracesFromCollector(
      @RequestParam String injectExpectationId, @RequestParam String sourceId) {
    Collector collector =
        collectorRepository
            .findById(sourceId)
            .orElseThrow(() -> new ElementNotFoundException("Collector not found"));
    return this.injectExpectationTraceService.getInjectExpectationTracesFromCollector(
        injectExpectationId, collector.getSecurityPlatform().getId());
  }

  @Operation(summary = "Get inject expectation traces' count")
  @GetMapping("/count")
  public long getAlertLinksNumber(
      @RequestParam String injectExpectationId,
      @RequestParam String sourceId,
      @RequestParam String expectationResultSourceType) {
    return this.injectExpectationTraceService.getAlertLinksNumber(
        injectExpectationId, sourceId, expectationResultSourceType);
  }
}
