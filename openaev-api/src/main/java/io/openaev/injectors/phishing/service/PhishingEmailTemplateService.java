package io.openaev.injectors.phishing.service;

import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.helper.StreamHelper;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for reusable phishing email templates. A template is a Component like a landing page but
 * does not carry its own contract; instead every landing page contract exposes the available
 * templates as a choice field, so any template mutation re-syncs the landing page contracts to keep
 * the chooser current.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PhishingEmailTemplateService {

  private final PhishingEmailTemplateRepository emailTemplateRepository;
  private final PhishingLandingPageService landingPageService;

  public List<PhishingEmailTemplate> emailTemplates() {
    return StreamHelper.fromIterable(emailTemplateRepository.findAll());
  }

  public PhishingEmailTemplate emailTemplate(@NotBlank final String id) {
    return emailTemplateRepository.findById(id).orElseThrow(ElementNotFoundException::new);
  }

  public PhishingEmailTemplate upsert(@NotNull final PhishingEmailTemplate emailTemplate) {
    emailTemplate.setUpdatedAt(Instant.now());
    PhishingEmailTemplate saved = emailTemplateRepository.save(emailTemplate);
    resyncLandingPageContracts();
    return saved;
  }

  public void delete(@NotBlank final String id) {
    if (!emailTemplateRepository.findById(id).isPresent()) {
      throw new ElementNotFoundException();
    }
    emailTemplateRepository.deleteById(id);
    resyncLandingPageContracts();
  }

  /** Rebuilds every landing page contract so the email-template chooser reflects current rows. */
  private void resyncLandingPageContracts() {
    landingPageService.landingPages().forEach(landingPageService::synchroniseInjectorContract);
  }
}
