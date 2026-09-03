package io.openaev.rest.asset.security_platforms;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.*;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformInput;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformUpsertInput;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class SecurityPlatformApi {

  public static final String SECURITY_PLATFORM_URI = "/api/security_platforms";
  private static final String TENANT_SECURITY_PLATFORM_URI = TENANT_PREFIX + "/security_platforms";

  @Value("${info.app.version:unknown}")
  String version;

  private final SecurityPlatformRepository securityPlatformRepository;
  private final DocumentRepository documentRepository;
  private final TagRepository tagRepository;
  private final DocumentService documentService;
  private final InjectorRepository injectorRepository;

  /**
   * The {@code collectors} table is v2 tenant-active: any read of it in a transaction without a
   * tenant scope is fail-closed EMPTY. {@code security_platform_collectors} is what the UI uses to
   * keep a collector-managed platform read-only, and it is rendered from the lazy {@code
   * collectors} association during JSON serialization — after the transaction (open-in-view), where
   * the transaction-local scope is gone. Every endpoint feeding that UI signal (GET list, GET by
   * id, POST search, PUT update) must therefore declare a {@link TxCtx} (the transaction aspect
   * writes it into the scope) AND initialize the association inside that scoped transaction through
   * this helper, or every platform silently unlocks in the UI (issue #7025).
   *
   * <p>{@code security_platform_injectors} (injector-registered platforms such as Nuclei, #7063)
   * feeds the same UI signal and is initialized here for the same reason: {@code injectors} is not
   * v2 tenant-active yet, but the association is lazy and rendered open-in-view, so it must load
   * inside the scoped transaction to stay correct when that table is activated.
   *
   * <p>Create and upsert are exempt: create returns a brand-new entity whose empty in-memory
   * associations serialize without a database load, and upsert is the connector-facing registration
   * endpoint whose response the UI never consumes.
   */
  private static SecurityPlatform withManagerLinksInitialized(SecurityPlatform securityPlatform) {
    Hibernate.initialize(securityPlatform.getCollectors());
    Hibernate.initialize(securityPlatform.getInjectors());
    return securityPlatform;
  }

  @GetMapping({SECURITY_PLATFORM_URI, TENANT_SECURITY_PLATFORM_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SECURITY_PLATFORM)
  // TxCtx scopes the transaction to the caller's tenants so the collectors association loads
  // (fail-closed empty otherwise); see withManagerLinksInitialized.
  public Iterable<SecurityPlatform> securityPlatforms(TxCtx ctx) {
    return fromIterable(securityPlatformRepository.findAll()).stream()
        .map(SecurityPlatformApi::withManagerLinksInitialized)
        .toList();
  }

  @PostMapping({SECURITY_PLATFORM_URI, TENANT_SECURITY_PLATFORM_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SECURITY_PLATFORM)
  @Transactional(rollbackFor = Exception.class)
  public SecurityPlatform createSecurityPlatform(
      @Valid @RequestBody final SecurityPlatformInput input, TxCtx ctx) {
    SecurityPlatform securityPlatform = new SecurityPlatform();
    securityPlatform.setUpdateAttributes(input);
    securityPlatform.setSecurityPlatformType(input.getSecurityPlatformType());
    if (input.getLogoDark() != null) {
      securityPlatform.setLogoDark(documentRepository.findById(input.getLogoDark()).orElse(null));
    } else {
      securityPlatform.setLogoDark(null);
    }
    if (input.getLogoLight() != null) {
      securityPlatform.setLogoLight(documentRepository.findById(input.getLogoLight()).orElse(null));
    } else {
      securityPlatform.setLogoLight(null);
    }
    securityPlatform.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.securityPlatformRepository.save(securityPlatform);
  }

  @PostMapping({SECURITY_PLATFORM_URI + "/upsert", TENANT_SECURITY_PLATFORM_URI + "/upsert"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SECURITY_PLATFORM)
  @Transactional(rollbackFor = Exception.class)
  public SecurityPlatform upsertSecurityPlatform(
      @Valid @RequestBody SecurityPlatformUpsertInput input, TxCtx ctx) {
    // A collector redeployed through the Integration Manager registers with a freshly
    // generated collector id (the external reference), while the platform row created by
    // the previous deployment still exists: fall back to the unique (name, type) pair so
    // the upsert updates that row (adopting the new external reference through
    // setUpdateAttributes) instead of failing on unique_security_platform_name_type_ci_idx.
    SecurityPlatform securityPlatform =
        securityPlatformRepository
            .findByExternalReference(input.getExternalReference())
            .or(
                () ->
                    securityPlatformRepository.findByNameIgnoreCaseAndSecurityPlatformType(
                        input.getName(), input.getSecurityPlatformType()))
            .orElseGet(SecurityPlatform::new);
    securityPlatform.setUpdateAttributes(input);
    securityPlatform.setSecurityPlatformType(input.getSecurityPlatformType());
    if (input.getLogoDark() != null) {
      securityPlatform.setLogoDark(documentRepository.findById(input.getLogoDark()).orElse(null));
    } else {
      securityPlatform.setLogoDark(null);
    }
    if (input.getLogoLight() != null) {
      securityPlatform.setLogoLight(documentRepository.findById(input.getLogoLight()).orElse(null));
    } else {
      securityPlatform.setLogoLight(null);
    }
    securityPlatform.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    SecurityPlatform saved = this.securityPlatformRepository.save(securityPlatform);
    linkRegisteringInjector(saved, input.getExternalReference());
    return saved;
  }

  /**
   * An injector that declares itself as a security platform (e.g. Nuclei registering as a
   * VULNERABILITY_SCANNER) upserts the platform keyed on its own injector type as the external
   * reference — that is the documented contract of the registration call. Restore the injector ->
   * platform link here so the platform stays read-only in the UI while the injector lives, and so a
   * redeployed injector self-heals the link its previous deployment lost when it was deleted from
   * the catalog (FK is ON DELETE SET NULL). Collector external references are collector ids and
   * never match an injector type, so collector registrations are unaffected (#7063).
   *
   * <p>The lookup is scoped by the persisted platform row's tenant, not {@link TenantContext}: the
   * thread-local falls back to the default tenant when unset (e.g. a call on the non-tenant URI),
   * which could link the default tenant's injector to another tenant's platform.
   */
  private void linkRegisteringInjector(
      SecurityPlatform securityPlatform, String externalReference) {
    if (externalReference == null || externalReference.isBlank()) {
      return;
    }
    injectorRepository
        .findBySecurityPlatformExternalReferenceByTenantId(
            externalReference, securityPlatform.getTenant().getId())
        .stream()
        .filter(injector -> injector.getSecurityPlatform() == null)
        .forEach(
            injector -> {
              injector.setSecurityPlatform(securityPlatform);
              injectorRepository.save(injector);
            });
  }

  @GetMapping({
    SECURITY_PLATFORM_URI + "/{securityPlatformId}",
    TENANT_SECURITY_PLATFORM_URI + "/{securityPlatformId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#securityPlatformId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SECURITY_PLATFORM)
  // TxCtx scopes the transaction so the collectors association loads; see
  // withManagerLinksInitialized.
  public SecurityPlatform securityPlatform(
      TxCtx ctx, @PathVariable @NotBlank final String securityPlatformId) {
    return this.securityPlatformRepository
        .findById(securityPlatformId)
        .map(SecurityPlatformApi::withManagerLinksInitialized)
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping({SECURITY_PLATFORM_URI + "/search", TENANT_SECURITY_PLATFORM_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SECURITY_PLATFORM)
  // TxCtx scopes the transaction so the collectors association loads; see
  // withManagerLinksInitialized.
  public Page<SecurityPlatform> securityPlatforms(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
            this.securityPlatformRepository::findAll, searchPaginationInput, SecurityPlatform.class)
        .map(SecurityPlatformApi::withManagerLinksInitialized);
  }

  @PutMapping({
    SECURITY_PLATFORM_URI + "/{securityPlatformId}",
    TENANT_SECURITY_PLATFORM_URI + "/{securityPlatformId}"
  })
  @AccessControl(
      resourceId = "#securityPlatformId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SECURITY_PLATFORM)
  @Transactional(rollbackFor = Exception.class)
  // TxCtx scopes the transaction so the response's collectors association loads (the UI replaces
  // its local state with this payload); see withManagerLinksInitialized.
  public SecurityPlatform updateSecurityPlatform(
      TxCtx ctx,
      @PathVariable @NotBlank final String securityPlatformId,
      @Valid @RequestBody final SecurityPlatformInput input) {
    SecurityPlatform securityPlatform =
        this.securityPlatformRepository.findById(securityPlatformId).orElseThrow();
    securityPlatform.setUpdateAttributes(input);
    if (input.getLogoDark() != null) {
      securityPlatform.setLogoDark(documentRepository.findById(input.getLogoDark()).orElse(null));
    } else {
      securityPlatform.setLogoDark(null);
    }
    if (input.getLogoLight() != null) {
      securityPlatform.setLogoLight(documentRepository.findById(input.getLogoLight()).orElse(null));
    } else {
      securityPlatform.setLogoLight(null);
    }
    securityPlatform.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return withManagerLinksInitialized(this.securityPlatformRepository.save(securityPlatform));
  }

  @DeleteMapping({
    SECURITY_PLATFORM_URI + "/{securityPlatformId}",
    TENANT_SECURITY_PLATFORM_URI + "/{securityPlatformId}"
  })
  @AccessControl(
      resourceId = "#securityPlatformId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SECURITY_PLATFORM)
  @Transactional(rollbackFor = Exception.class)
  public void deleteSecurityPlatform(@PathVariable @NotBlank final String securityPlatformId) {
    this.securityPlatformRepository.deleteById(securityPlatformId);
  }

  @GetMapping({
    SECURITY_PLATFORM_URI + "/{securityPlatformId}/documents",
    TENANT_SECURITY_PLATFORM_URI + "/{securityPlatformId}/documents"
  })
  @AccessControl(
      resourceId = "#securityPlatformId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SECURITY_PLATFORM)
  @Operation(summary = "Get the Documents used in a security platform")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Documents used in the security platform")
      })
  public List<RawDocument> documentsFromSecurityPlatform(@PathVariable String securityPlatformId) {
    return documentService.documentsForSecurityPlatform(securityPlatformId);
  }

  @GetMapping({SECURITY_PLATFORM_URI + "/options", TENANT_SECURITY_PLATFORM_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SECURITY_PLATFORM)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText, TxCtx ctx) {
    return securityPlatformRepository.findAllByName(StringUtils.trimToNull(searchText)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping({SECURITY_PLATFORM_URI + "/options", TENANT_SECURITY_PLATFORM_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SECURITY_PLATFORM)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids, TxCtx ctx) {
    return fromIterable(this.securityPlatformRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
