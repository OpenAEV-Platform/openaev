package io.openaev.api.attack_pattern;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.attack_pattern.dto.AttackPatternCoverageOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping({AttackPatternCoverageApi.ATTACK_PATTERN_URI, TENANT_PREFIX + "/attack_patterns"})
public class AttackPatternCoverageApi {

  public static final String ATTACK_PATTERN_URI = "/api/attack_patterns";

  private final AttackPatternService attackPatternService;

  // -- READ --

  @GetMapping("/coverage")
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ATTACK_PATTERN)
  @Operation(
      summary = "Global MITRE ATT&CK coverage",
      description =
          "Tenant-wide ATT&CK matrix aggregating prevention and detection results across simulations (Elasticsearch, identical to the home security-coverage matrix)")
  public List<AttackPatternCoverageOutput> attackPatternsCoverage(
      TxCtx ctx, @RequestParam(required = false) final Integer latest) {
    if (latest != null && latest < 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The 'latest' parameter must be a positive integer");
    }
    return attackPatternService.getGlobalCoverage(latest);
  }
}
