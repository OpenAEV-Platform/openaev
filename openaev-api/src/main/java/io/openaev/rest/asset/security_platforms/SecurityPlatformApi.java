package io.openaev.rest.asset.security_platforms;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.RBAC;
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
import java.util.Optional;
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

  @Value("${info.app.version:unknown}")
  String version;

  private final SecurityPlatformRepository securityPlatformRepository;
  private final DocumentRepository documentRepository;
  private final TagRepository tagRepository;
  private final DocumentService documentService;

  @GetMapping(SECURITY_PLATFORM_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SECURITY_PLATFORM)
  public Iterable<SecurityPlatform> securityPlatforms() {
    return securityPlatformRepository.findAll();
  }

  @PostMapping(SECURITY_PLATFORM_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SECURITY_PLATFORM)
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

  @PostMapping(SECURITY_PLATFORM_URI + "/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SECURITY_PLATFORM)
  @Transactional(rollbackFor = Exception.class)
  public SecurityPlatform upsertSecurityPlatform(
      @Valid @RequestBody SecurityPlatformUpsertInput input) {
    Optional<SecurityPlatform> securityPlatform =
        securityPlatformRepository.findByExternalReference(input.getExternalReference());
    if (securityPlatform.isPresent()) {
      SecurityPlatform existingSecurityPlatform = securityPlatform.get();
      existingSecurityPlatform.setUpdateAttributes(input);
      existingSecurityPlatform.setSecurityPlatformType(input.getSecurityPlatformType());
      if (input.getLogoDark() != null) {
        existingSecurityPlatform.setLogoDark(
            documentRepository.findById(input.getLogoDark()).orElse(null));
      } else {
        existingSecurityPlatform.setLogoDark(null);
      }
      if (input.getLogoLight() != null) {
        existingSecurityPlatform.setLogoLight(
            documentRepository.findById(input.getLogoLight()).orElse(null));
      } else {
        existingSecurityPlatform.setLogoLight(null);
      }
      existingSecurityPlatform.setTags(
          iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
      return this.securityPlatformRepository.save(existingSecurityPlatform);
    } else {
      SecurityPlatform newSecurityPlatform = new SecurityPlatform();
      newSecurityPlatform.setUpdateAttributes(input);
      newSecurityPlatform.setSecurityPlatformType(input.getSecurityPlatformType());
      if (input.getLogoDark() != null) {
        newSecurityPlatform.setLogoDark(
            documentRepository.findById(input.getLogoDark()).orElse(null));
      } else {
        newSecurityPlatform.setLogoDark(null);
      }
      if (input.getLogoLight() != null) {
        newSecurityPlatform.setLogoLight(
            documentRepository.findById(input.getLogoLight()).orElse(null));
      } else {
        newSecurityPlatform.setLogoLight(null);
      }
      newSecurityPlatform.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
      return this.securityPlatformRepository.save(newSecurityPlatform);
    }
  }

  @GetMapping(SECURITY_PLATFORM_URI + "/{securityPlatformId}")
  @RBAC(
      resourceId = "#securityPlatformId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SECURITY_PLATFORM)
  public SecurityPlatform securityPlatform(
      @PathVariable @NotBlank final String securityPlatformId) {
    return this.securityPlatformRepository
        .findById(securityPlatformId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping(SECURITY_PLATFORM_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SECURITY_PLATFORM)
  public Page<SecurityPlatform> securityPlatforms(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        this.securityPlatformRepository::findAll, searchPaginationInput, SecurityPlatform.class);
  }

  @PutMapping(SECURITY_PLATFORM_URI + "/{securityPlatformId}")
  @RBAC(
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

  @DeleteMapping(SECURITY_PLATFORM_URI + "/{securityPlatformId}")
  @RBAC(
      resourceId = "#securityPlatformId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SECURITY_PLATFORM)
  @Transactional(rollbackFor = Exception.class)
  public void deleteSecurityPlatform(@PathVariable @NotBlank final String securityPlatformId) {
    this.securityPlatformRepository.deleteById(securityPlatformId);
  }

  @GetMapping(SECURITY_PLATFORM_URI + "/{securityPlatformId}/documents")
  @RBAC(
      resourceId = "#securityPlatformId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SECURITY_PLATFORM)
  @Operation(summary = "Get the Documents used in a security platform")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Documents used in the security platform")
      })
  public List<RawDocument> documentsFromSecurityPlatform(@PathVariable String securityPlatformId) {
    return documentService.documentsForSecurityPlatform(securityPlatformId);
  }

  @GetMapping(SECURITY_PLATFORM_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SECURITY_PLATFORM)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return securityPlatformRepository.findAllByName(StringUtils.trimToNull(searchText)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(SECURITY_PLATFORM_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SECURITY_PLATFORM)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.securityPlatformRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
