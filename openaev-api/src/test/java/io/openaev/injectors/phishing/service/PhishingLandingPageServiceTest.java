package io.openaev.injectors.phishing.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.injectors.phishing.PhishingContract;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phishing landing page service tests")
class PhishingLandingPageServiceTest {

  @Mock private PhishingLandingPageRepository landingPageRepository;
  @Mock private PhishingEmailTemplateRepository emailTemplateRepository;
  @Mock private InjectorRepository injectorRepository;
  @Mock private InjectorContractRepository injectorContractRepository;
  @Mock private ExpectationBuilderService expectationBuilderService;
  @Mock private PhishingContract phishingContract;
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
}
