package io.openaev.rest.expectation;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.InjectExpectationBulkUpdateInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.service.InjectExpectationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ExpectationApi extends RestBehavior {

  public static final String EXPECTATIONS_URI = "/api/expectations";
  public static final String INJECTS_EXPECTATIONS_URI = "/api/injects/expectations";

  private final InjectExpectationService injectExpectationService;

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(EXPECTATIONS_URI + "/{expectationId}")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public InjectExpectation updateInjectExpectation(
      @PathVariable @NotBlank final String expectationId,
      @Valid @RequestBody final ExpectationUpdateInput input) {
    return injectExpectationService.updateInjectExpectation(expectationId, input);
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(EXPECTATIONS_URI + "/{expectationId}/{sourceId}/delete")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public InjectExpectation deleteInjectExpectationResult(
      @PathVariable @NotBlank final String expectationId,
      @PathVariable @NotBlank final String sourceId) {
    return injectExpectationService.deleteInjectExpectationResult(expectationId, sourceId);
  }

  @Operation(
      summary = "Get Inject Expectations",
      description =
          "Retrieves inject expectations of agents installed on an asset. If an expiration time is provided, it will return all expectations not expired within this timeframe independently of their results. Otherwise, it will return all expectations without any result.")
  @GetMapping(INJECTS_EXPECTATIONS_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<InjectExpectation> getInjectExpectationsNotFilledOrNotExpired(
      @RequestParam(required = false, name = "expiration_time") final Integer expirationTime) {
    if (expirationTime == null) {
      return Stream.of(
              injectExpectationService.manualExpectationsNotFill(),
              injectExpectationService.preventionExpectationsNotFill(),
              injectExpectationService.detectionExpectationsNotFill())
          .flatMap(List::stream)
          .toList();
    }

    return Stream.of(
            injectExpectationService.manualExpectationsNotExpired(expirationTime),
            injectExpectationService.preventionExpectationsNotExpired(expirationTime),
            injectExpectationService.detectionExpectationsNotExpired(expirationTime))
        .flatMap(List::stream)
        .toList();
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source",
      description =
          "Retrieves inject expectations that have not seen any result yet of agents installed on an asset for a given source ID.")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/{sourceId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<InjectExpectation> getInjectExpectationsNotFilledForSource(
      @PathVariable String sourceId) {
    return Stream.concat(
            injectExpectationService.manualExpectationsNotFill(sourceId).stream(),
            Stream.concat(
                injectExpectationService.preventionExpectationsNotFill(sourceId).stream(),
                injectExpectationService.detectionExpectationsNotFill(sourceId).stream()))
        .toList();
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID.")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/assets/{sourceId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<InjectExpectation> getInjectExpectationsAssetsNotFilledForSource(
      @PathVariable String sourceId,
      @RequestParam(required = false, name = "expiration_time") final Integer expirationTime) {
    if (expirationTime == null) {
      return Stream.concat(
              injectExpectationService.preventionExpectationsNotFill(sourceId).stream(),
              injectExpectationService.detectionExpectationsNotFill(sourceId).stream())
          .toList();
    }
    return Stream.concat(
            injectExpectationService.preventionExpectationsNotExpired(expirationTime).stream(),
            injectExpectationService.detectionExpectationsNotExpired(expirationTime).stream())
        .toList();
  }

  @GetMapping(INJECTS_EXPECTATIONS_URI + "/prevention")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<InjectExpectation> getInjectPreventionExpectationsNotFilled() {
    return injectExpectationService.preventionExpectationsNotFill().stream().toList();
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source and type Prevention",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID and type Prevention.")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/prevention/{sourceId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<InjectExpectation> getInjectPreventionExpectationsNotFilledForSource(
      @PathVariable String sourceId) {
    return injectExpectationService.preventionExpectationsNotFill(sourceId).stream().toList();
  }

  @GetMapping(INJECTS_EXPECTATIONS_URI + "/detection")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<InjectExpectation> getInjectDetectionExpectationsNotFilled() {
    return injectExpectationService.detectionExpectationsNotFill().stream().toList();
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source and type Detection",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID and type detection.")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/detection/{sourceId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<InjectExpectation> getInjectDetectionExpectationsNotFilledForSource(
      @PathVariable String sourceId) {
    return injectExpectationService.detectionExpectationsNotFill(sourceId).stream().toList();
  }

  @Operation(
      summary = "Update Inject Expectation",
      description = "Update Inject expectation from an external source, e.g., EDR collector.")
  @PutMapping(INJECTS_EXPECTATIONS_URI + "/{expectationId}")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public InjectExpectation updateInjectExpectation(
      @PathVariable @NotBlank final String expectationId,
      @Valid @RequestBody @NotNull InjectExpectationUpdateInput input) {
    return injectExpectationService.updateInjectExpectation(expectationId, input);
  }

  @Operation(
      summary = "Bulk Update Inject Expectation",
      description = "Bulk Update Inject expectation from an external source, e.g., EDR collector.")
  @PutMapping(INJECTS_EXPECTATIONS_URI + "/bulk")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public void updateInjectExpectation(
      @Valid @RequestBody @NotNull InjectExpectationBulkUpdateInput inputs) {
    injectExpectationService.bulkUpdateInjectExpectation(inputs.getInputs());
  }
}
