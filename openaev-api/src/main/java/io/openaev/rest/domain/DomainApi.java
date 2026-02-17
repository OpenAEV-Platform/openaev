package io.openaev.rest.domain;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
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
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Domain API", description = "Operations related to Domain")
public class DomainApi extends RestBehavior {

  public static final String DOMAIN_URI = "/api/domains";
  private final DomainService domainService;

  @LogExecutionTime
  @Operation(summary = "Search Domains")
  @GetMapping(DOMAIN_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PAYLOAD)
  public List<Domain> domains() {
    return domainService.searchDomains();
  }

  @Operation(summary = "Get a Domain by ID", description = "Fetches detailed Domain info by ID")
  @GetMapping(DOMAIN_URI + "/{domainId}")
  @RBAC(
      resourceId = "#domainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PAYLOAD)
  public Domain getDomain(@PathVariable String domainId) {
    return domainService.findById(domainId);
  }

  @PostMapping(DOMAIN_URI + "/{domainId}/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The upserted domain")})
  @Operation(description = "Upsert a domain", summary = "Upsert domain")
  public Domain upsertDomain(@Valid @RequestBody DomainBaseInput input) {
    return domainService.upsert(input);
  }

  // -- OPTION --

  @GetMapping(DOMAIN_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOMAIN)
  public List<FilterUtilsJpa.Option> findAllAsOptionsByName(
      @RequestParam(required = false) final String searchText) {
    return domainService.findAllAsOptionsByName(searchText);
  }

  @PostMapping(DOMAIN_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOMAIN)
  public List<FilterUtilsJpa.Option> findAllAsOptionsById(@RequestBody final List<String> ids) {
    return domainService.findAllAsOptionsById(ids);
  }
}
