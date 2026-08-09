package io.openaev.injectors.phishing.service;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;

import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.helper.StreamHelper;
import io.openaev.injectors.phishing.form.PhishingEmailTemplateBulkProcessingInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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

  public Page<PhishingEmailTemplate> search(@NotNull final SearchPaginationInput input) {
    return buildPaginationJPA(
        (Specification<PhishingEmailTemplate> specification, Pageable pageable) ->
            emailTemplateRepository.findAll(specification, pageable),
        input,
        PhishingEmailTemplate.class);
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

  /**
   * Bulk delete of email templates, either from an explicit list of ids or from a search input
   * (select all with optional exclusions). Landing page contracts are re-synced once at the end so
   * the email-template chooser reflects the remaining rows.
   *
   * @param input the bulk processing input (exactly one of ids / search input must be provided)
   * @return the ids of the deleted email templates
   */
  public List<String> bulkDelete(@NotNull final PhishingEmailTemplateBulkProcessingInput input) {
    boolean hasIds = !CollectionUtils.isEmpty(input.getEmailTemplateIdsToProcess());
    boolean hasSearch = input.getSearchPaginationInput() != null;
    if (hasIds == hasSearch) {
      throw new BadRequestException(
          "Either email_template_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }

    Specification<PhishingEmailTemplate> specification;
    if (hasSearch) {
      // Same specification chain as the list search (filter group + text search), so the deletion
      // scope matches exactly what the user sees in the list.
      specification =
          FilterUtilsJpa.<PhishingEmailTemplate>computeFilterGroupJpa(
                  input.getSearchPaginationInput().getFilterGroup())
              .and(computeSearchJpa(input.getSearchPaginationInput().getTextSearch()));
    } else {
      specification = SpecificationUtils.hasIdIn(input.getEmailTemplateIdsToProcess());
    }
    if (!CollectionUtils.isEmpty(input.getEmailTemplateIdsToIgnore())) {
      List<String> idsToIgnore = input.getEmailTemplateIdsToIgnore();
      specification =
          specification.and((root, query, cb) -> cb.not(root.get("id").in(idsToIgnore)));
    }

    List<String> idsToDelete =
        emailTemplateRepository.findAll(specification).stream()
            .map(PhishingEmailTemplate::getId)
            .toList();
    if (idsToDelete.isEmpty()) {
      return idsToDelete;
    }
    emailTemplateRepository.deleteAllById(idsToDelete);
    resyncLandingPageContracts();
    return idsToDelete;
  }

  /** Rebuilds every landing page contract so the email-template chooser reflects current rows. */
  private void resyncLandingPageContracts() {
    landingPageService.landingPages().forEach(landingPageService::synchroniseInjectorContract);
  }
}
