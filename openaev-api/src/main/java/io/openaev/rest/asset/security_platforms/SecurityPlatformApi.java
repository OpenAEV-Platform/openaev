package io.openaev.rest.asset.security_platforms;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
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

  @GetMapping({SECURITY_PLATFORM_URI, TENANT_SECURITY_PLATFORM_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SECURITY_PLATFORM)
  public Iterable<SecurityPlatform> securityPlatforms() {
    return securityPlatformRepository.findAll();
  }

  @PostMapping({SECURITY_PLATFORM_URI, TENANT_SECURITY_PLATFORM_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SECURITY_PLATFORM)
  @Transactional(rollbackFor = Exception.class)
  public SecurityPlatform createSecurityPlatform(
      @Valid @RequestBody final SecurityPlatformInput input) {
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
      @Valid @RequestBody SecurityPlatformUpsertInput input) {
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
    return this.securityPlatformRepository.save(securityPlatform);
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
  public SecurityPlatform securityPlatform(
      @PathVariable @NotBlank final String securityPlatformId) {
    return this.securityPlatformRepository
        .findById(securityPlatformId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping({SECURITY_PLATFORM_URI + "/search", TENANT_SECURITY_PLATFORM_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SECURITY_PLATFORM)
  public Page<SecurityPlatform> securityPlatforms(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        this.securityPlatformRepository::findAll, searchPaginationInput, SecurityPlatform.class);
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
  public SecurityPlatform updateSecurityPlatform(
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
    return this.securityPlatformRepository.save(securityPlatform);
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
      @RequestParam(required = false) final String searchText) {
    return securityPlatformRepository.findAllByName(StringUtils.trimToNull(searchText)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping({SECURITY_PLATFORM_URI + "/options", TENANT_SECURITY_PLATFORM_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SECURITY_PLATFORM)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.securityPlatformRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
