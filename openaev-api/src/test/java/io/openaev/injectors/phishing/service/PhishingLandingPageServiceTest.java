package io.openaev.injectors.phishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Document;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.injectors.phishing.PhishingContract;
import io.openaev.injectors.phishing.form.PhishingLandingPageBulkProcessingInput;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.organization.OrganizationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phishing landing page service tests")
class PhishingLandingPageServiceTest {

  @Mock private PhishingLandingPageRepository landingPageRepository;
  @Mock private PhishingEmailTemplateRepository emailTemplateRepository;
  @Mock private InjectorRepository injectorRepository;
  @Mock private InjectorContractRepository injectorContractRepository;
  @Mock private ExpectationBuilderService expectationBuilderService;
  @Mock private PhishingContract phishingContract;
  @Mock private DocumentService documentService;
  @Mock private DomainService domainService;
  @Mock private OrganizationService organizationService;
  @Mock private ObjectMapper mapper;

  @InjectMocks private PhishingLandingPageService phishingLandingPageService;

  @Test
  @DisplayName(
      "synchroniseInjectorContract is a no-op when the phishing injector is not registered")
  void synchronise_should_beNoopWhenInjectorMissing() {
    // -- ARRANGE --
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");
    when(injectorRepository.findByTypeAndTenantId(eq(PhishingContract.TYPE), any()))
        .thenReturn(Optional.empty());

    // -- ACT --
    InjectorContract contract = phishingLandingPageService.synchroniseInjectorContract(landingPage);

    // -- ASSERT --
    assertNull(contract);
    verify(injectorContractRepository, never()).save(any(InjectorContract.class));
  }

  @Test
  @DisplayName("delete removes the landing page and its synthesized injector contract")
  void delete_should_removeLandingPageAndContract() {
    // -- ARRANGE --
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");
    when(landingPageRepository.findById("lp-1")).thenReturn(Optional.of(landingPage));
    when(injectorContractRepository.existsById(any(InjectorContractId.class))).thenReturn(true);

    // -- ACT --
    phishingLandingPageService.delete("lp-1");

    // -- ASSERT --
    verify(injectorContractRepository).deleteById(any(InjectorContractId.class));
    verify(landingPageRepository).deleteById("lp-1");
  }

  @Test
  @DisplayName("updateLogos resolves documents through the service and clears a null logo")
  void updateLogos_should_resolveDocumentsAndClearNulls() {
    // -- ARRANGE --
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");
    Document darkLogo = new Document();
    darkLogo.setId("doc-dark");
    when(landingPageRepository.findById("lp-1")).thenReturn(Optional.of(landingPage));
    when(documentService.document("doc-dark")).thenReturn(darkLogo);
    when(landingPageRepository.save(any(PhishingLandingPage.class))).thenReturn(landingPage);
    when(injectorRepository.findByTypeAndTenantId(eq(PhishingContract.TYPE), any()))
        .thenReturn(Optional.empty());

    // -- ACT --
    PhishingLandingPage updated = phishingLandingPageService.updateLogos("lp-1", "doc-dark", null);

    // -- ASSERT --
    assertSame(darkLogo, updated.getLogoDark());
    assertNull(updated.getLogoLight());
    verify(documentService).document("doc-dark");
  }

  @Test
  @DisplayName("upsert rejects a javascript: redirect URL and never persists it")
  void upsert_should_rejectDangerousRedirectScheme() {
    // -- ARRANGE --
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");
    landingPage.setRedirectUrl("javascript:alert(document.cookie)");

    // -- ACT / ASSERT --
    assertThrows(BadRequestException.class, () -> phishingLandingPageService.upsert(landingPage));
    verify(landingPageRepository, never()).save(any(PhishingLandingPage.class));
  }

  @Test
  @DisplayName("upsert rejects a javascript: redirect URL disguised with control characters")
  void upsert_should_rejectRedirectSchemeWithControlChars() {
    // -- ARRANGE --
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");
    landingPage.setRedirectUrl("java\tscript:alert(1)");

    // -- ACT / ASSERT --
    assertThrows(BadRequestException.class, () -> phishingLandingPageService.upsert(landingPage));
    verify(landingPageRepository, never()).save(any(PhishingLandingPage.class));
  }

  @Test
  @DisplayName("upsert accepts relative and http(s) redirect URLs")
  void upsert_should_acceptRelativeAndHttpRedirectUrls() {
    // -- ARRANGE --
    when(landingPageRepository.save(any(PhishingLandingPage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(injectorRepository.findByTypeAndTenantId(eq(PhishingContract.TYPE), any()))
        .thenReturn(Optional.empty());

    for (String url :
        new String[] {"/dashboard", "https://example.com/next", "http://example.com"}) {
      PhishingLandingPage landingPage = new PhishingLandingPage();
      landingPage.setId("lp-1");
      landingPage.setName("Login page");
      landingPage.setRedirectUrl(url);

      // -- ACT --
      PhishingLandingPage saved = phishingLandingPageService.upsert(landingPage);

      // -- ASSERT --
      assertSame(landingPage, saved);
    }
  }

  @Test
  @DisplayName("bulkDelete removes every resolved landing page and returns their ids")
  void bulkDelete_should_deleteResolvedLandingPages() {
    // -- ARRANGE --
    PhishingLandingPage first = new PhishingLandingPage();
    first.setId("lp-1");
    first.setName("First");
    PhishingLandingPage second = new PhishingLandingPage();
    second.setId("lp-2");
    second.setName("Second");
    when(landingPageRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(first, second));
    when(landingPageRepository.findById("lp-1")).thenReturn(Optional.of(first));
    when(landingPageRepository.findById("lp-2")).thenReturn(Optional.of(second));
    when(injectorContractRepository.existsById(any(InjectorContractId.class))).thenReturn(false);

    PhishingLandingPageBulkProcessingInput input = new PhishingLandingPageBulkProcessingInput();
    input.setLandingPageIdsToProcess(List.of("lp-1", "lp-2"));

    // -- ACT --
    List<String> deleted = phishingLandingPageService.bulkDelete(input);

    // -- ASSERT --
    assertEquals(List.of("lp-1", "lp-2"), deleted);
    verify(landingPageRepository).deleteById("lp-1");
    verify(landingPageRepository).deleteById("lp-2");
  }

  @Test
  @DisplayName("bulkDelete rejects an input with neither ids nor a search input")
  void bulkDelete_should_rejectEmptyInput() {
    // -- ARRANGE --
    PhishingLandingPageBulkProcessingInput input = new PhishingLandingPageBulkProcessingInput();

    // -- ACT / ASSERT --
    assertThrows(BadRequestException.class, () -> phishingLandingPageService.bulkDelete(input));
    verify(landingPageRepository, never()).deleteById(any());
  }
}
