package io.openaev.injectors.phishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Document;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Organization;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.helper.SupportedLanguage;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.injectors.phishing.PhishingContract;
import io.openaev.injectors.phishing.form.PhishingLandingPageBulkProcessingInput;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.service.InjectIndexCleanupService;
import io.openaev.service.organization.OrganizationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
  @Mock private InjectIndexCleanupService injectIndexCleanupService;
  @Mock private AttackPatternRepository attackPatternRepository;
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
  @DisplayName(
      "synchroniseInjectorContract associates the phishing Spearphishing Link MITRE techniques")
  void synchronise_should_setPhishingAttackPatterns() throws Exception {
    // -- ARRANGE --
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");

    Injector injector = new Injector();
    injector.setId("phishing-injector");
    when(injectorRepository.findByTypeAndTenantId(eq(PhishingContract.TYPE), any()))
        .thenReturn(Optional.of(injector));
    when(injectorContractRepository.findById(any(InjectorContractId.class)))
        .thenReturn(Optional.empty());

    when(phishingContract.getConfig())
        .thenReturn(
            new ContractConfig(
                PhishingContract.TYPE,
                Map.of(SupportedLanguage.en, "Phishing"),
                "#000000",
                "#ffffff",
                null));
    when(emailTemplateRepository.findAll()).thenReturn(List.of());
    // Predefined expectations are built through the mocked builder; return real instances so the
    // buildPhishingExpectations mutations (setPredefined, setName, ...) don't NPE.
    when(expectationBuilderService.buildPreventionExpectation()).thenReturn(new Expectation());
    when(expectationBuilderService.buildDetectionExpectation()).thenReturn(new Expectation());
    when(expectationBuilderService.buildManualExpectation()).thenReturn(new Expectation());
    when(domainService.upsertDomainEntities(any(), any())).thenReturn(Set.of());
    when(organizationService.findOrCreateByName(any())).thenReturn(new Organization());

    AttackPattern spearphishingLink = new AttackPattern();
    spearphishingLink.setExternalId("T1566.002");
    AttackPattern phishingForInfo = new AttackPattern();
    phishingForInfo.setExternalId("T1598.003");
    when(attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(any(), any()))
        .thenReturn(List.of(spearphishingLink, phishingForInfo));

    when(mapper.writeValueAsString(any())).thenReturn("{}");
    when(mapper.readValue(anyString(), eq(ObjectNode.class)))
        .thenReturn(JsonNodeFactory.instance.objectNode());
    when(injectorContractRepository.save(any(InjectorContract.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // -- ACT --
    InjectorContract result = phishingLandingPageService.synchroniseInjectorContract(landingPage);

    // -- ASSERT --
    // The two "Spearphishing Link" techniques are resolved by external id and set on the contract.
    // Kill chain phases are derived from these patterns, so associating them is all that is
    // required.
    verify(attackPatternRepository)
        .findAllByExternalIdInIgnoreCaseAndTenantId(eq(List.of("T1566.002", "T1598.003")), any());
    assertEquals(List.of(spearphishingLink, phishingForInfo), result.getAttackPatterns());
  }

  @Test
  @DisplayName(
      "synchroniseInjectorContract declares a Credentials output when the page captures data")
  void synchronise_should_declareCredentialsOutputWhenCapturing() throws Exception {
    // -- ARRANGE --
    // A capture-enabled page turns submitted data into a Credentials finding, so the synthesized
    // contract must declare that output - otherwise getProviding() (and the Threat Arsenal drawer)
    // report the action as producing nothing.
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");
    landingPage.setCaptureSubmittedData(true);
    arrangeSynchroniseStubs();

    ArgumentCaptor<Object> contractCaptor = ArgumentCaptor.forClass(Object.class);

    // -- ACT --
    phishingLandingPageService.synchroniseInjectorContract(landingPage);

    // -- ASSERT --
    verify(mapper).writeValueAsString(contractCaptor.capture());
    Contract serialized = (Contract) contractCaptor.getValue();
    List<ContractOutputType> declared =
        serialized.getOutputs().stream()
            .map(InjectorContractContentOutputElement::getType)
            .toList();
    assertEquals(List.of(ContractOutputType.Credentials), declared);
    assertTrue(serialized.getOutputs().getFirst().isFindingCompatible());
  }

  @Test
  @DisplayName("synchroniseInjectorContract declares no output when the page does not capture data")
  void synchronise_should_declareNoOutputWhenNotCapturing() throws Exception {
    // -- ARRANGE --
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Awareness only");
    landingPage.setCaptureSubmittedData(false);
    arrangeSynchroniseStubs();

    ArgumentCaptor<Object> contractCaptor = ArgumentCaptor.forClass(Object.class);

    // -- ACT --
    phishingLandingPageService.synchroniseInjectorContract(landingPage);

    // -- ASSERT --
    verify(mapper).writeValueAsString(contractCaptor.capture());
    Contract serialized = (Contract) contractCaptor.getValue();
    assertTrue(serialized.getOutputs().isEmpty());
  }

  /**
   * Common stubs for a successful {@link
   * PhishingLandingPageService#synchroniseInjectorContract(PhishingLandingPage)} run: the phishing
   * injector is registered, no contract exists yet, and the config / lookups / (mocked) mapper all
   * resolve so the synthesized contract is built and captured.
   */
  private void arrangeSynchroniseStubs() throws Exception {
    Injector injector = new Injector();
    injector.setId("phishing-injector");
    when(injectorRepository.findByTypeAndTenantId(eq(PhishingContract.TYPE), any()))
        .thenReturn(Optional.of(injector));
    when(injectorContractRepository.findById(any(InjectorContractId.class)))
        .thenReturn(Optional.empty());
    when(phishingContract.getConfig())
        .thenReturn(
            new ContractConfig(
                PhishingContract.TYPE,
                Map.of(SupportedLanguage.en, "Phishing"),
                "#000000",
                "#ffffff",
                null));
    when(emailTemplateRepository.findAll()).thenReturn(List.of());
    when(expectationBuilderService.buildPreventionExpectation()).thenReturn(new Expectation());
    when(expectationBuilderService.buildDetectionExpectation()).thenReturn(new Expectation());
    when(expectationBuilderService.buildManualExpectation()).thenReturn(new Expectation());
    when(domainService.upsertDomainEntities(any(), any())).thenReturn(Set.of());
    when(organizationService.findOrCreateByName(any())).thenReturn(new Organization());
    when(attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(any(), any()))
        .thenReturn(List.of());
    when(mapper.writeValueAsString(any())).thenReturn("{}");
    when(mapper.readValue(anyString(), eq(ObjectNode.class)))
        .thenReturn(JsonNodeFactory.instance.objectNode());
    when(injectorContractRepository.save(any(InjectorContract.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
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
  @DisplayName("delete de-indexes the injects cascade-deleted with the injector contract")
  void delete_should_deindexCascadeDeletedInjects() {
    // -- ARRANGE --
    // injects.inject_injector_contract is ON DELETE CASCADE: the DB silently hard-deletes the
    // injects built on the contract, so no JPA lifecycle event fires for them. The service must
    // collect the doomed inject ids BEFORE the delete and notify the engine AFTER, mirroring
    // InjectorContractService.deleteInjectorContract.
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");
    when(landingPageRepository.findById("lp-1")).thenReturn(Optional.of(landingPage));
    when(injectorContractRepository.existsById(any(InjectorContractId.class))).thenReturn(true);
    when(injectIndexCleanupService.injectIdsByContractIds(eq(List.of("lp-1")), any()))
        .thenReturn(List.of("inject-1", "inject-2"));

    // -- ACT --
    phishingLandingPageService.delete("lp-1");

    // -- ASSERT --
    InOrder inOrder = inOrder(injectIndexCleanupService, injectorContractRepository);
    inOrder.verify(injectIndexCleanupService).injectIdsByContractIds(eq(List.of("lp-1")), any());
    inOrder.verify(injectorContractRepository).deleteById(any(InjectorContractId.class));
    inOrder
        .verify(injectIndexCleanupService)
        .notifyEngineOfDeletedInjects(List.of("inject-1", "inject-2"));
  }

  @Test
  @DisplayName("delete skips the de-index compensation when no contract exists")
  void delete_should_skipDeindexWhenNoContract() {
    // -- ARRANGE --
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setId("lp-1");
    landingPage.setName("Login page");
    when(landingPageRepository.findById("lp-1")).thenReturn(Optional.of(landingPage));
    when(injectorContractRepository.existsById(any(InjectorContractId.class))).thenReturn(false);

    // -- ACT --
    phishingLandingPageService.delete("lp-1");

    // -- ASSERT --
    verify(injectorContractRepository, never()).deleteById(any(InjectorContractId.class));
    verify(injectIndexCleanupService, never()).injectIdsByContractIds(any(), any());
    verify(injectIndexCleanupService, never()).notifyEngineOfDeletedInjects(any());
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
