package io.openaev.rest.inject;

import io.openaev.aop.RBAC;
import io.openaev.api.inject_result.dto.InjectResultPayloadExecutionOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InjectExecutionResultApi extends RestBehavior {

  public static final String INJECT_EXECUTION_URI = "/api/injects/{injectId}";

  private final InjectExecutionResultService injectExecutionService;

  @GetMapping(INJECT_EXECUTION_URI + "/execution-result")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public InjectResultPayloadExecutionOutput injectExecutionResultPayload(
      @PathVariable @NotBlank final String injectId,
      @RequestParam @NotBlank final String targetId,
      @RequestParam @NotNull final TargetType targetType) {
    return this.injectExecutionService.injectExecutionResultPayload(injectId, targetId, targetType);
  }
}
