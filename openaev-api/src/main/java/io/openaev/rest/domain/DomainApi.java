package io.openaev.rest.domain;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Domain;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.FilterUtilsJpa;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Domain API", description = "Operations related to Domain")
@RequestMapping({DomainApi.DOMAIN_URI, DomainApi.TENANT_DOMAIN_URI})
public class DomainApi extends RestBehavior {

  public static final String DOMAIN_URI = "/api/domains";
  public static final String TENANT_DOMAIN_URI = TENANT_PREFIX + "/domains";
  private final DomainService domainService;

  @LogExecutionTime
  @Operation(summary = "Search Domains")
  @Transactional
  @GetMapping
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.DOMAIN)
  public List<Domain> domains(TxCtx ctx) {
    return domainService.searchDomains();
  }

  @Operation(summary = "Get a Domain by ID", description = "Fetches detailed Domain info by ID")
  @Transactional
  @GetMapping("/{domainId}")
  @AccessControl(
      resourceId = "#domainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOMAIN)
  public Domain getDomain(@PathVariable String domainId) {
    return domainService.findById(domainId);
  }

  @PostMapping("/{domainId}/upsert")
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.DOMAIN)
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The upserted domain")})
  @Operation(description = "Upsert a domain", summary = "Upsert domain")
  public Domain upsertDomain(@Valid @RequestBody DomainBaseInput input, TxCtx ctx) {
    return domainService.upsert(input);
  }

  // -- OPTION --

  @GetMapping("/options")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOMAIN)
  public List<FilterUtilsJpa.Option> findAllAsOptionsByName(
      @RequestParam(required = false) final String searchText, TxCtx ctx) {
    return domainService.findAllAsOptionsByName(searchText);
  }

  @PostMapping("/options")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOMAIN)
  public List<FilterUtilsJpa.Option> findAllAsOptionsById(
      @RequestBody final List<String> ids, TxCtx ctx) {
    return domainService.findAllAsOptionsById(ids);
  }
}
