package io.openaev.injectors.phishing.service;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;
import static io.openaev.injector_contract.Contract.executableContract;
import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractCardinality.One;
import static io.openaev.injector_contract.ContractDef.contractBuilder;
import static io.openaev.injector_contract.fields.ContractExpectations.expectationsField;
import static io.openaev.injector_contract.fields.ContractSelect.selectFieldWithDefault;
import static io.openaev.injector_contract.fields.ContractTeam.teamField;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.custom_domain.CustomDomainService;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CustomDomain;
import io.openaev.database.model.CustomDomain.CustomDomainStatus;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.helper.StreamHelper;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.ContractDef;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.fields.ContractSelect;
import io.openaev.injectors.phishing.PhishingContract;
import io.openaev.injectors.phishing.form.PhishingLandingPageBulkProcessingInput;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.organization.OrganizationService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * CRUD for reusable phishing landing pages plus the dynamic {@link InjectorContract} synthesis that
 * makes each landing page appear as a Threat Arsenal action. Mirrors {@code
 * PayloadService.synchroniseInjectorContractBasedOnPayload}: every create/update rebuilds the
 * landing page's contract (id equal to the landing page id, bound to the tenant's phishing
 * injector), and delete removes it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PhishingLandingPageService {

  // Built-in phishing actions are shipped by the platform, so their contracts are authored by
  // Filigran - the same author every other built-in injector contract carries (see
  // InjectorService.BUILTIN_INJECTOR_AUTHOR).
  private static final String BUILTIN_INJECTOR_AUTHOR = "Filigran";

  private final PhishingLandingPageRepository landingPageRepository;
  private final PhishingEmailTemplateRepository emailTemplateRepository;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final ExpectationBuilderService expectationBuilderService;
  private final PhishingContract phishingContract;
  private final DocumentService documentService;
  private final DomainService domainService;
  private final OrganizationService organizationService;
  private final CustomDomainService customDomainService;
  private final ObjectMapper mapper;

  /**
   * Resolves a landing page's linked custom domain from an id, enforcing that it exists in the
   * tenant and is verified. A blank id clears the link (serve on the platform domain). A domain
   * that is not yet verified is rejected so a page can never point at an unusable hostname.
   */
  public CustomDomain resolveVerifiedCustomDomain(final String customDomainId) {
    if (customDomainId == null || customDomainId.isBlank()) {
      return null;
    }
    CustomDomain domain = customDomainService.customDomain(customDomainId);
    if (domain.getStatus() != CustomDomainStatus.VERIFIED) {
      throw new BadRequestException("The selected custom domain is not verified yet");
    }
    return domain;
  }

  // -- CRUD --

  public List<PhishingLandingPage> landingPages() {
    return StreamHelper.fromIterable(landingPageRepository.findAll());
  }

  public Page<PhishingLandingPage> search(@NotNull final SearchPaginationInput input) {
    return buildPaginationJPA(
        (Specification<PhishingLandingPage> specification, Pageable pageable) ->
            landingPageRepository.findAll(specification, pageable),
        input,
        PhishingLandingPage.class);
  }

  public PhishingLandingPage landingPage(@NotBlank final String id) {
    return landingPageRepository.findById(id).orElseThrow(ElementNotFoundException::new);
  }

  public PhishingLandingPage upsert(@NotNull final PhishingLandingPage landingPage) {
    validateRedirectUrl(landingPage.getRedirectUrl());
    landingPage.setUpdatedAt(Instant.now());
    PhishingLandingPage saved = landingPageRepository.save(landingPage);
    synchroniseInjectorContract(saved);
    return saved;
  }

  /**
   * Rejects redirect URLs that are neither a relative path nor an http(s) URL. The public landing
   * page assigns this value to {@code window.location.href} after the victim submits, so a {@code
   * javascript:} (or {@code data:}) scheme would execute script in the OpenAEV origin, bypassing
   * the page's DOMPurify sanitization. Only relative, {@code http}, and {@code https} targets are
   * allowed. Browsers strip ASCII control/whitespace characters before parsing a URL scheme, so the
   * value is normalized the same way before the scheme is inspected.
   */
  private void validateRedirectUrl(final String redirectUrl) {
    if (redirectUrl == null || redirectUrl.isBlank()) {
      return;
    }
    String normalized = redirectUrl.replaceAll("[\\u0000-\\u0020]", "");
    int schemeSeparator = normalized.indexOf(':');
    int firstSlash = normalized.indexOf('/');
    boolean hasScheme = schemeSeparator > 0 && (firstSlash < 0 || schemeSeparator < firstSlash);
    if (!hasScheme) {
      return;
    }
    String scheme = normalized.substring(0, schemeSeparator).toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new BadRequestException("Redirect URL must be a relative path or an http(s) URL");
    }
  }

  /**
   * Resolves and sets the dark/light logo documents of a landing page, keeping document resolution
   * in the service layer so the controller depends on services only. A {@code null} id clears the
   * corresponding logo; a non-null id that does not resolve is rejected by {@code
   * DocumentService.document}.
   */
  public PhishingLandingPage updateLogos(
      @NotBlank final String id, final String logoDarkId, final String logoLightId) {
    PhishingLandingPage landingPage = landingPage(id);
    landingPage.setLogoDark(logoDarkId != null ? documentService.document(logoDarkId) : null);
    landingPage.setLogoLight(logoLightId != null ? documentService.document(logoLightId) : null);
    return upsert(landingPage);
  }

  public void delete(@NotBlank final String id) {
    PhishingLandingPage landingPage = landingPage(id);
    deleteInjectorContract(landingPage);
    landingPageRepository.deleteById(id);
  }

  /**
   * Bulk delete of landing pages, either from an explicit list of ids or from a search input
   * (select all with optional exclusions). Each page is removed through the regular {@link
   * #delete(String)} path so its synthesized Threat Arsenal contract is cleaned up too.
   *
   * @param input the bulk processing input (exactly one of ids / search input must be provided)
   * @return the ids of the deleted landing pages
   */
  public List<String> bulkDelete(@NotNull final PhishingLandingPageBulkProcessingInput input) {
    boolean hasIds = !CollectionUtils.isEmpty(input.getLandingPageIdsToProcess());
    boolean hasSearch = input.getSearchPaginationInput() != null;
    if (hasIds == hasSearch) {
      throw new BadRequestException(
          "Either landing_page_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }

    Specification<PhishingLandingPage> specification;
    if (hasSearch) {
      // Same specification chain as the list search (filter group + text search), so the deletion
      // scope matches exactly what the user sees in the list.
      specification =
          FilterUtilsJpa.<PhishingLandingPage>computeFilterGroupJpa(
                  input.getSearchPaginationInput().getFilterGroup())
              .and(computeSearchJpa(input.getSearchPaginationInput().getTextSearch()));
    } else {
      specification = SpecificationUtils.hasIdIn(input.getLandingPageIdsToProcess());
    }
    if (!CollectionUtils.isEmpty(input.getLandingPageIdsToIgnore())) {
      List<String> idsToIgnore = input.getLandingPageIdsToIgnore();
      specification =
          specification.and((root, query, cb) -> cb.not(root.get("id").in(idsToIgnore)));
    }

    List<String> idsToDelete =
        landingPageRepository.findAll(specification).stream()
            .map(PhishingLandingPage::getId)
            .toList();
    idsToDelete.forEach(this::delete);
    return idsToDelete;
  }

  // -- SEED --

  /**
   * Seeds a platform-themed default email template and login landing page for the current tenant
   * when none exist yet. Called once per tenant at startup (after the phishing injector is
   * registered), so the seeded landing page immediately gets its Threat Arsenal contract.
   * Idempotent on the tenant scope: the tenant filter is active during per-tenant registration, so
   * existing rows short-circuit both seeds. Always ends with {@link #resyncAllContracts()} so
   * existing landing pages keep (or regain) arsenal actions after injector re-registration.
   */
  public void seedDefaultsIfEmpty() {
    if (!emailTemplateRepository.findAll().iterator().hasNext()) {
      PhishingEmailTemplate emailTemplate = new PhishingEmailTemplate();
      emailTemplate.setName("Default lure email");
      emailTemplate.setDescription("A simple, reusable lure email created by the platform.");
      emailTemplate.setSubject("Action required: verify your account");
      emailTemplate.setHtmlBody(
          """
          <p>Hello,</p>
          <p>We detected unusual activity on your account. Please verify your identity to keep
          your access active.</p>
          <p><a href="{{phishing_url}}">Verify my account</a></p>
          <p>If you did not expect this message, you can ignore it.</p>
          """);
      emailTemplate.setAddTrackingPixel(true);
      emailTemplateRepository.save(emailTemplate);
    }

    if (!landingPageRepository.findAll().iterator().hasNext()) {
      PhishingLandingPage landingPage = new PhishingLandingPage();
      landingPage.setName("Default login page");
      landingPage.setDescription("A simple, platform-themed credential capture page.");
      landingPage.setHtml(
          """
          <div class="phishing-card">
            <h1>Sign in</h1>
            <p>Please sign in to continue.</p>
            <form data-phishing-form>
              <label>Email<input type="email" name="username" autocomplete="username" required /></label>
              <label>Password<input type="password" name="password" autocomplete="current-password" required /></label>
              <button type="submit">Sign in</button>
            </form>
          </div>
          """);
      landingPage.setCss(
          """
          .phishing-card { max-width: 360px; margin: 10vh auto; padding: 2rem;
            border-radius: 8px; font-family: sans-serif; }
          .phishing-card label { display: block; margin: 0.75rem 0; }
          .phishing-card input { width: 100%; padding: 0.5rem; box-sizing: border-box; }
          .phishing-card button { margin-top: 1rem; padding: 0.6rem 1.2rem; cursor: pointer; }
          """);
      landingPage.setCaptureSubmittedData(true);
      landingPage.setCapturePasswords(true);
      // upsert already synchronises the contract; resyncAllContracts below is still needed for
      // tenants that already had landing pages before a wipe / upgrade.
      upsert(landingPage);
    }

    resyncAllContracts();
  }

  /**
   * Rebuilds the Threat Arsenal contract for every landing page in the current tenant. Safe to call
   * after injector registration and after email-template mutations that change select choices.
   */
  public void resyncAllContracts() {
    landingPages().forEach(this::synchroniseInjectorContract);
  }

  // -- CONTRACT SYNC --

  /**
   * Creates or updates the {@link InjectorContract} backing this landing page (Threat Arsenal
   * action). No-op when the phishing injector is not yet registered for the tenant (e.g. startup
   * ordering); the next update re-syncs.
   */
  public InjectorContract synchroniseInjectorContract(
      @NotNull final PhishingLandingPage landingPage) {
    String tenantId = resolveTenantId(landingPage);
    Injector injector =
        injectorRepository.findByTypeAndTenantId(PhishingContract.TYPE, tenantId).orElse(null);
    if (injector == null) {
      log.warn("Phishing injector not registered for tenant {}, skipping contract sync", tenantId);
      return null;
    }

    InjectorContract injectorContract =
        injectorContractRepository
            .findById(new InjectorContractId(landingPage.getId(), tenantId))
            .orElseGet(
                () -> {
                  InjectorContract created = new InjectorContract();
                  created.setId(landingPage.getId());
                  return created;
                });

    Contract contract = buildContract(landingPage, injector, tenantId);

    // Prefix so Threat Arsenal search / category browsing makes the phishing origin obvious.
    Map<String, String> labels =
        Map.of(
            "en",
            "Phishing: " + landingPage.getName(),
            "fr",
            "Hameconnage : " + landingPage.getName());
    injectorContract.setLabels(labels);
    injectorContract.addInjector(injector);

    // The Threat Arsenal and the atomic-testing picker read these entity columns, NOT the
    // serialized
    // content JSON. Populate them from the synthesized contract exactly like a static built-in
    // contract does (InjectorContractService.applyBuiltinContractData), otherwise the action shows
    // no
    // platform / domain / author and is filtered out of the atomic-testing picker
    // (injector_contract_atomic_testing = true).
    injectorContract.setManual(contract.isManual());
    injectorContract.setAtomicTesting(contract.isAtomicTesting());
    injectorContract.setNeedsExecutor(contract.isNeedsExecutor());
    injectorContract.setPlatforms(contract.getPlatforms().toArray(new Endpoint.PLATFORM_TYPE[0]));
    injectorContract.setDomains(
        this.domainService.upsertDomainEntities(contract.getDomains(), tenantId));
    injectorContract.setAuthorOrganization(
        this.organizationService.findOrCreateByName(BUILTIN_INJECTOR_AUTHOR));

    try {
      String content = mapper.writeValueAsString(contract);
      injectorContract.setContent(content);
      injectorContract.setConvertedContent(mapper.readValue(content, ObjectNode.class));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    return injectorContractRepository.save(injectorContract);
  }

  public void deleteInjectorContract(@NotNull final PhishingLandingPage landingPage) {
    String tenantId = resolveTenantId(landingPage);
    InjectorContractId contractId = new InjectorContractId(landingPage.getId(), tenantId);
    if (injectorContractRepository.existsById(contractId)) {
      injectorContractRepository.deleteById(contractId);
    }
  }

  private String resolveTenantId(final PhishingLandingPage landingPage) {
    return landingPage.getTenant() != null
        ? landingPage.getTenant().getId()
        : TenantContext.getCurrentTenant();
  }

  private Contract buildContract(
      final PhishingLandingPage landingPage, final Injector injector, final String tenantId) {
    ContractConfig contractConfig = phishingContract.getConfig();

    // Email template chooser populated from the tenant's reusable templates.
    Map<String, String> templateChoices = new LinkedHashMap<>();
    List<PhishingEmailTemplate> templates =
        StreamHelper.fromIterable(emailTemplateRepository.findAll());
    templates.forEach(template -> templateChoices.put(template.getId(), template.getName()));
    ContractSelect emailTemplateField =
        templateChoices.isEmpty()
            ? new ContractSelect("emailTemplate", "Email template", One)
            : selectFieldWithDefault(
                "emailTemplate",
                "Email template",
                templateChoices,
                templateChoices.keySet().iterator().next());
    emailTemplateField.setChoices(templateChoices);

    ContractDef builder = contractBuilder();
    List<ContractElement> fields =
        builder
            .mandatory(teamField(Multiple))
            .mandatory(emailTemplateField)
            .optional(textField("subject", "Subject override", ""))
            .optional(textField("fromName", "Sender name override", ""))
            .optional(textField("fromEmail", "Sender email override", ""))
            .optional(expectationsField(buildPhishingExpectations()))
            .build();

    // Server-side email delivery, exactly like the email injector: platform Service and no executor
    // agent. A recipient is a team player (teamField below), never an endpoint, so needsExecutor
    // stays false - otherwise target search would demand agents and the health check would fail.
    return executableContract(
        contractConfig,
        landingPage.getId(),
        Map.of(
            en, "Phishing: " + landingPage.getName(), fr, "Hameconnage : " + landingPage.getName()),
        fields,
        List.of(Endpoint.PLATFORM_TYPE.Service),
        false,
        Set.of(PresetDomain.getEmailInfiltration()));
  }

  /**
   * Available expectations for a phishing action. Like most technical injector contracts, phishing
   * carries PREVENTION and DETECTION on top of the always-available MANUAL expectation: a phishing
   * simulation is only meaningful if the platform can score whether the lure was blocked
   * (prevention) or detected (detection). Both are predefined so every phishing inject measures
   * them by default, and both are focused on {@link
   * SecurityPlatform.SECURITY_PLATFORM_TYPE#EMAIL_SECURITY} platforms - the security control that
   * actually inspects inbound mail - instead of asking every connected collector for a verdict.
   */
  private List<Expectation> buildPhishingExpectations() {
    Expectation prevention = this.expectationBuilderService.buildPreventionExpectation();
    prevention.setPredefined(true);
    prevention.setExpectedSecurityPlatformTypes(
        new ArrayList<>(List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.EMAIL_SECURITY)));

    Expectation detection = this.expectationBuilderService.buildDetectionExpectation();
    detection.setPredefined(true);
    detection.setExpectedSecurityPlatformTypes(
        new ArrayList<>(List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.EMAIL_SECURITY)));

    return List.of(detection, prevention, this.expectationBuilderService.buildManualExpectation());
  }
}
