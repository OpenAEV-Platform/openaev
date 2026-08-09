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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.helper.StreamHelper;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.ContractDef;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.fields.ContractSelect;
import io.openaev.injectors.phishing.PhishingContract;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private final PhishingLandingPageRepository landingPageRepository;
  private final PhishingEmailTemplateRepository emailTemplateRepository;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final ExpectationBuilderService expectationBuilderService;
  private final PhishingContract phishingContract;
  private final DocumentService documentService;
  private final ObjectMapper mapper;

  // -- CRUD --

  public List<PhishingLandingPage> landingPages() {
    return StreamHelper.fromIterable(landingPageRepository.findAll());
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

  // -- SEED --

  /**
   * Seeds a platform-themed default email template and login landing page for the current tenant
   * when none exist yet. Called once per tenant at startup (after the phishing injector is
   * registered), so the seeded landing page immediately gets its Threat Arsenal contract.
   * Idempotent on the tenant scope: the tenant filter is active during per-tenant registration, so
   * existing rows short-circuit both seeds.
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
      upsert(landingPage);
    }
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

    Map<String, String> labels = Map.of("en", landingPage.getName(), "fr", landingPage.getName());
    injectorContract.setLabels(labels);
    injectorContract.setNeedsExecutor(true);
    injectorContract.setManual(false);
    injectorContract.setAtomicTesting(false);
    injectorContract.addInjector(injector);

    try {
      Contract contract = buildContract(landingPage, injector, tenantId);
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
            .optional(
                expectationsField(List.of(this.expectationBuilderService.buildManualExpectation())))
            .build();

    return executableContract(
        contractConfig,
        landingPage.getId(),
        Map.of(en, landingPage.getName(), fr, landingPage.getName()),
        fields,
        List.of(Endpoint.PLATFORM_TYPE.Internal),
        false,
        Set.of(PresetDomain.getEmailInfiltration()));
  }
}
