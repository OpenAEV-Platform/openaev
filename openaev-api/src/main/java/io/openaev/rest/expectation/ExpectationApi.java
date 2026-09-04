package io.openaev.rest.expectation;

import static io.openaev.api.expectations.mapper.InjectExpectationMapper.toOutput;
import static io.openaev.api.expectations.mapper.InjectExpectationMapper.toOutputs;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.expectations.dto.InjectExpectationOutput;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.InjectExpectationBulkUpdateInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.service.InjectExpectationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
public class ExpectationApi extends RestBehavior {

  public static final String EXPECTATIONS_URI = "/api/expectations";
  public static final String TENANT_EXPECTATIONS_URI = TENANT_PREFIX + "/expectations";
  public static final String INJECTS_EXPECTATIONS_URI = "/api/injects/expectations";
  public static final String TENANT_INJECTS_EXPECTATIONS_URI =
      TENANT_PREFIX + "/injects/expectations";

  private final InjectExpectationService injectExpectationService;

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({EXPECTATIONS_URI + "/{expectationId}", TENANT_EXPECTATIONS_URI + "/{expectationId}"})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  // TxCtx scopes the transaction so the collectors table (v2-activated) is visible.
  public InjectExpectationOutput updateInjectExpectation(
      TxCtx ctx,
      @PathVariable @NotBlank final String expectationId,
      @Valid @RequestBody final ExpectationUpdateInput input) {
    log.debug(
        "PUT expectation {} with source={} ({}), score={}",
        expectationId,
        input.getSourceId(),
        input.getSourceName(),
        input.getScore());
    return toOutput(injectExpectationService.updateInjectExpectation(expectationId, input));
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    EXPECTATIONS_URI + "/{expectationId}/{sourceId}/delete",
    TENANT_EXPECTATIONS_URI + "/{expectationId}/{sourceId}/delete"
  })
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public InjectExpectationOutput deleteInjectExpectationResult(
      // Unused by the handler body; TenantScopeTransactionAspect reads it to set the tenant scope
      // for this transaction.
      TxCtx ctx,
      @PathVariable @NotBlank final String expectationId,
      @PathVariable @NotBlank final String sourceId) {
    return toOutput(
        injectExpectationService.deleteInjectExpectationResult(expectationId, sourceId));
  }

  @Operation(
      summary = "Get Inject Expectations",
      description =
          "Retrieves inject expectations of agents installed on an asset. If an expiration time is provided, it will return all expectations not expired within this timeframe independently of their results. Otherwise, it will return all expectations without any result.")
  @GetMapping({INJECTS_EXPECTATIONS_URI, TENANT_INJECTS_EXPECTATIONS_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional
  public List<InjectExpectationOutput> getInjectExpectationsNotFilledAndNotExpired(
      TxCtx ctx,
      @RequestParam(required = false, name = "expiration_time") final Integer expirationTime) {
    String tenantId = TenantContext.getCurrentTenant();
    if (expirationTime == null) {
      return toOutputs(
          Stream.of(
                  injectExpectationService.manualExpectationsNotFill(tenantId),
                  injectExpectationService.preventionExpectationsNotFill(tenantId),
                  injectExpectationService.detectionExpectationsNotFill(tenantId))
              .flatMap(List::stream)
              .toList());
    }

    return toOutputs(
        Stream.of(
                injectExpectationService.manualExpectationsNotFillAndNotExpired(
                    tenantId, expirationTime),
                injectExpectationService.preventionExpectationsNotFillAndNotExpired(
                    tenantId, expirationTime),
                injectExpectationService.detectionExpectationsNotFillAndNotExpired(
                    tenantId, expirationTime))
            .flatMap(List::stream)
            .toList());
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source",
      description =
          "Retrieves inject expectations that have not seen any result yet of agents installed on an asset for a given source ID.")
  @GetMapping({
    INJECTS_EXPECTATIONS_URI + "/{sourceId}",
    TENANT_INJECTS_EXPECTATIONS_URI + "/{sourceId}"
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional
  public List<InjectExpectationOutput> getInjectExpectationsNotFilledForSource(
      TxCtx ctx, @PathVariable String sourceId) {
    String tenantId = TenantContext.getCurrentTenant();
    return toOutputs(
        Stream.concat(
                injectExpectationService.manualExpectationsNotFill(tenantId, sourceId).stream(),
                Stream.concat(
                    injectExpectationService
                        .preventionExpectationsNotFill(tenantId, sourceId)
                        .stream(),
                    injectExpectationService
                        .detectionExpectationsNotFill(tenantId, sourceId)
                        .stream()))
            .toList());
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID.")
  @GetMapping({
    INJECTS_EXPECTATIONS_URI + "/assets/{sourceId}",
    TENANT_INJECTS_EXPECTATIONS_URI + "/assets/{sourceId}"
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional
  public List<InjectExpectationOutput> getInjectExpectationsAssetsNotFilledAndNotExpiredForSource(
      TxCtx ctx,
      @PathVariable String sourceId,
      @RequestParam(required = false, name = "expiration_time") final Integer expirationTime) {
    String tenantId = TenantContext.getCurrentTenant();
    if (expirationTime == null) {
      return toOutputs(
          Stream.concat(
                  injectExpectationService
                      .preventionExpectationsNotFill(tenantId, sourceId)
                      .stream(),
                  injectExpectationService
                      .detectionExpectationsNotFill(tenantId, sourceId)
                      .stream())
              .toList());
    }
    return toOutputs(
        Stream.concat(
                injectExpectationService
                    .preventionExpectationsNotFilledAndNotExpired(
                        tenantId, expirationTime, sourceId)
                    .stream(),
                injectExpectationService
                    .detectionExpectationsNotFilledAndNotExpired(tenantId, expirationTime, sourceId)
                    .stream())
            .toList());
  }

  @GetMapping({
    INJECTS_EXPECTATIONS_URI + "/prevention",
    TENANT_INJECTS_EXPECTATIONS_URI + "/prevention"
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional
  public List<InjectExpectationOutput> getInjectPreventionExpectationsNotFilled(TxCtx ctx) {
    String tenantId = TenantContext.getCurrentTenant();
    return toOutputs(
        injectExpectationService.preventionExpectationsNotFill(tenantId).stream().toList());
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source and type Prevention",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID and type Prevention.")
  @GetMapping({
    INJECTS_EXPECTATIONS_URI + "/prevention/{sourceId}",
    TENANT_INJECTS_EXPECTATIONS_URI + "/prevention/{sourceId}"
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional
  public List<InjectExpectationOutput> getInjectPreventionExpectationsNotFilledForSource(
      TxCtx ctx, @PathVariable String sourceId) {
    String tenantId = TenantContext.getCurrentTenant();
    List<InjectExpectationOutput> results =
        toOutputs(
            injectExpectationService.preventionExpectationsNotFill(tenantId, sourceId).stream()
                .toList());
    log.debug(
        "GET prevention expectations for source {} (tenant {}): {} pending",
        sourceId,
        tenantId,
        results.size());
    return results;
  }

  @GetMapping({
    INJECTS_EXPECTATIONS_URI + "/detection",
    TENANT_INJECTS_EXPECTATIONS_URI + "/detection"
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional
  public List<InjectExpectationOutput> getInjectDetectionExpectationsNotFilled(TxCtx ctx) {
    String tenantId = TenantContext.getCurrentTenant();
    return toOutputs(
        injectExpectationService.detectionExpectationsNotFill(tenantId).stream().toList());
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source and type Detection",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID and type detection.")
  @GetMapping({
    INJECTS_EXPECTATIONS_URI + "/detection/{sourceId}",
    TENANT_INJECTS_EXPECTATIONS_URI + "/detection/{sourceId}"
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional
  public List<InjectExpectationOutput> getInjectDetectionExpectationsNotFilledForSource(
      TxCtx ctx, @PathVariable String sourceId) {
    String tenantId = TenantContext.getCurrentTenant();
    List<InjectExpectationOutput> results =
        toOutputs(
            injectExpectationService.detectionExpectationsNotFill(tenantId, sourceId).stream()
                .toList());
    log.debug(
        "GET detection expectations for source {} (tenant {}): {} pending",
        sourceId,
        tenantId,
        results.size());
    return results;
  }

  @Operation(
      summary = "Get agentless AI defense Inject Expectations for a Specific Source",
      description =
          "Retrieves agentless DETECTION/PREVENTION inject expectations not yet filled for a given source ID. Used by AI defense collectors (LLM firewall / guardrail) to validate AI adversarial injects, whose targets are AI models/agents rather than endpoints with an installed agent.")
  @GetMapping({
    INJECTS_EXPECTATIONS_URI + "/ai/{sourceId}",
    TENANT_INJECTS_EXPECTATIONS_URI + "/ai/{sourceId}"
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional
  // TxCtx scopes the transaction so the collectors table (v2-activated) is visible: the feed's
  // expected-security-platforms guard reads collectors natively, and without a scope the guard is
  // fail-closed empty, silently dropping every platform-restricted expectation (#7014).
  public List<InjectExpectationOutput> getAiDefenseExpectationsNotFilledForSource(
      TxCtx ctx, @PathVariable String sourceId) {
    String tenantId = TenantContext.getCurrentTenant();
    return toOutputs(injectExpectationService.aiDefenseExpectationsNotFill(tenantId, sourceId));
  }

  @Operation(
      summary = "Update Inject Expectation",
      description = "Update Inject expectation from an external source, e.g., EDR collector.")
  @PutMapping({
    INJECTS_EXPECTATIONS_URI + "/{expectationId}",
    TENANT_INJECTS_EXPECTATIONS_URI + "/{expectationId}"
  })
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  // TxCtx scopes the transaction so the collectors table (v2-activated) is visible.
  public InjectExpectationOutput updateInjectExpectation(
      TxCtx ctx,
      @PathVariable @NotBlank final String expectationId,
      @Valid @RequestBody @NotNull InjectExpectationUpdateInput input) {
    return toOutput(injectExpectationService.updateInjectExpectation(expectationId, input));
  }

  @Operation(
      summary = "Bulk Update Inject Expectation",
      description = "Bulk Update Inject expectation from an external source, e.g., EDR collector.")
  @PutMapping({INJECTS_EXPECTATIONS_URI + "/bulk", TENANT_INJECTS_EXPECTATIONS_URI + "/bulk"})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  // TxCtx scopes the transaction so the collectors table (v2-activated) is visible.
  public void updateInjectExpectation(
      TxCtx ctx, @Valid @RequestBody @NotNull InjectExpectationBulkUpdateInput inputs) {
    injectExpectationService.bulkUpdateInjectExpectation(inputs.getInputs());
  }
}
