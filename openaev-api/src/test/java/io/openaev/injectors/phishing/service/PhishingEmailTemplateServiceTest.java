package io.openaev.injectors.phishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.injectors.phishing.form.PhishingEmailTemplateBulkProcessingInput;
import io.openaev.rest.exception.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phishing email template service tests")
class PhishingEmailTemplateServiceTest {

  @Mock private PhishingEmailTemplateRepository emailTemplateRepository;
  @Mock private PhishingLandingPageService landingPageService;

  @InjectMocks private PhishingEmailTemplateService phishingEmailTemplateService;

  @Test
  @DisplayName(
      "bulkDelete removes every resolved template and re-syncs landing page contracts once")
  void bulkDelete_should_deleteResolvedTemplatesAndResyncOnce() {
    // -- ARRANGE --
    PhishingEmailTemplate first = new PhishingEmailTemplate();
    first.setId("et-1");
    first.setName("First");
    first.setTenant(new Tenant("tenant-1"));
    PhishingEmailTemplate second = new PhishingEmailTemplate();
    second.setId("et-2");
    second.setName("Second");
    second.setTenant(new Tenant("tenant-1"));
    when(emailTemplateRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(first, second));

    PhishingEmailTemplateBulkProcessingInput input = new PhishingEmailTemplateBulkProcessingInput();
    input.setEmailTemplateIdsToProcess(List.of("et-1", "et-2"));

    // -- ACT --
    List<String> deleted = phishingEmailTemplateService.bulkDelete(input);

    // -- ASSERT --
    assertEquals(List.of("et-1", "et-2"), deleted);
    verify(emailTemplateRepository).deleteAllById(List.of("et-1", "et-2"));
    // A single re-sync for the whole batch, not one per deleted template.
    verify(landingPageService).resyncAllContracts("tenant-1");
  }

  @Test
  @DisplayName(
      "bulkDelete resyncs every distinct tenant reachable through a multi-tenant scope, not just"
          + " the first matched template's")
  void bulkDelete_should_resyncEveryDistinctTenantInScope() {
    // -- ARRANGE --
    PhishingEmailTemplate tenantOneTemplate = new PhishingEmailTemplate();
    tenantOneTemplate.setId("et-1");
    tenantOneTemplate.setName("First");
    tenantOneTemplate.setTenant(new Tenant("tenant-1"));
    PhishingEmailTemplate tenantTwoTemplate = new PhishingEmailTemplate();
    tenantTwoTemplate.setId("et-2");
    tenantTwoTemplate.setName("Second");
    tenantTwoTemplate.setTenant(new Tenant("tenant-2"));
    when(emailTemplateRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(tenantOneTemplate, tenantTwoTemplate));

    PhishingEmailTemplateBulkProcessingInput input = new PhishingEmailTemplateBulkProcessingInput();
    input.setEmailTemplateIdsToProcess(List.of("et-1", "et-2"));

    // -- ACT --
    List<String> deleted = phishingEmailTemplateService.bulkDelete(input);

    // -- ASSERT --
    assertEquals(List.of("et-1", "et-2"), deleted);
    verify(emailTemplateRepository).deleteAllById(List.of("et-1", "et-2"));
    // A multi-id X-Tenant-Ids scope can legitimately match templates of several tenants: every one
    // of them must have its landing page contracts resynced, not only the first matched template's.
    verify(landingPageService).resyncAllContracts("tenant-1");
    verify(landingPageService).resyncAllContracts("tenant-2");
  }

  @Test
  @DisplayName("bulkDelete is a no-op that skips the re-sync when nothing matches")
  void bulkDelete_should_skipResyncWhenNothingMatches() {
    // -- ARRANGE --
    when(emailTemplateRepository.findAll(any(Specification.class))).thenReturn(List.of());

    PhishingEmailTemplateBulkProcessingInput input = new PhishingEmailTemplateBulkProcessingInput();
    input.setEmailTemplateIdsToProcess(List.of("missing"));

    // -- ACT --
    List<String> deleted = phishingEmailTemplateService.bulkDelete(input);

    // -- ASSERT --
    assertTrue(deleted.isEmpty());
    verify(emailTemplateRepository, never()).deleteAllById(any());
    verify(landingPageService, never()).resyncAllContracts(any());
  }

  @Test
  @DisplayName("bulkDelete rejects an input providing both ids and a search input")
  void bulkDelete_should_rejectAmbiguousInput() {
    // -- ARRANGE --
    PhishingEmailTemplateBulkProcessingInput input = new PhishingEmailTemplateBulkProcessingInput();
    input.setEmailTemplateIdsToProcess(List.of("et-1"));
    input.setSearchPaginationInput(new io.openaev.utils.pagination.SearchPaginationInput());

    // -- ACT / ASSERT --
    assertThrows(BadRequestException.class, () -> phishingEmailTemplateService.bulkDelete(input));
    verify(emailTemplateRepository, never()).deleteAllById(any());
  }
}
