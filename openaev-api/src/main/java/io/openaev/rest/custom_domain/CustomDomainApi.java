package io.openaev.rest.custom_domain;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.CustomDomain;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.custom_domain.form.CustomDomainInput;
import io.openaev.rest.custom_domain.response.CustomDomainInstructions;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin CRUD + DNS verification for custom domains (Settings &gt; Customization &gt; Custom
 * domains). Gated on {@link ResourceType#TENANT_SETTING} - a custom domain is a tenant-level
 * customization, so it reuses the same RBAC as the other tenant settings instead of introducing a
 * new capability.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Custom domains", description = "Manage customer-owned hostnames for phishing pages")
public class CustomDomainApi extends RestBehavior {

  public static final String CUSTOM_DOMAIN_URI = "/api/custom-domains";
  private static final String TENANT_CUSTOM_DOMAIN_URI = TENANT_PREFIX + "/custom-domains";

  private final CustomDomainService customDomainService;

  @PostMapping({CUSTOM_DOMAIN_URI + "/search", TENANT_CUSTOM_DOMAIN_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @Operation(summary = "Search custom domains")
  public Page<CustomDomain> searchCustomDomains(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return customDomainService.search(searchPaginationInput);
  }

  @GetMapping({CUSTOM_DOMAIN_URI + "/{id}", TENANT_CUSTOM_DOMAIN_URI + "/{id}"})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @Operation(summary = "Get a custom domain")
  public CustomDomain customDomain(@PathVariable String id) {
    return customDomainService.customDomain(id);
  }

  @GetMapping({
    CUSTOM_DOMAIN_URI + "/{id}/instructions",
    TENANT_CUSTOM_DOMAIN_URI + "/{id}/instructions"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @Operation(summary = "Get the DNS records to publish for a custom domain")
  public CustomDomainInstructions instructions(@PathVariable String id) {
    return customDomainService.instructions(id);
  }

  @PostMapping({CUSTOM_DOMAIN_URI, TENANT_CUSTOM_DOMAIN_URI})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @Operation(summary = "Register a custom domain")
  public CustomDomain createCustomDomain(@Valid @RequestBody CustomDomainInput input) {
    return customDomainService.create(input.getHostname());
  }

  @PostMapping({CUSTOM_DOMAIN_URI + "/{id}/verify", TENANT_CUSTOM_DOMAIN_URI + "/{id}/verify"})
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @Operation(summary = "Run the DNS ownership verification for a custom domain")
  public CustomDomain verifyCustomDomain(@PathVariable String id) {
    return customDomainService.verify(id);
  }

  @DeleteMapping({CUSTOM_DOMAIN_URI + "/{id}", TENANT_CUSTOM_DOMAIN_URI + "/{id}"})
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @Operation(summary = "Delete a custom domain")
  public void deleteCustomDomain(@PathVariable String id) {
    customDomainService.delete(id);
  }
}
