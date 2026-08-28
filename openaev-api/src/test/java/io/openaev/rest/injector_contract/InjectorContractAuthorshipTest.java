package io.openaev.rest.injector_contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.openaev.config.OpenAEVPrincipal;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.inject.service.InjectIndexCleanupService;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.service.InjectorService;
import io.openaev.service.UserService;
import io.openaev.service.organization.OrganizationService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Contract authorship must be a function of the creation's provenance (machine sync vs interactive
 * creation), never of which credentials happened to authenticate the HTTP call: external injector
 * sync loops (e.g. Nuclei per-CVE contracts) authenticate their background calls with the
 * registering user's token, so a real session user is present during machine syncs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Provenance-based contract authorship")
class InjectorContractAuthorshipTest {

  private static final String PUBLISHER = "Nuclei";

  @Mock private InjectorContractRepository injectorContractRepository;
  @Mock private AttackPatternService attackPatternService;
  @Mock private VulnerabilityService vulnerabilityService;
  @Mock private DomainService domainService;
  @Mock private InjectorRepository injectorRepository;
  @Mock private UserService userService;
  @Mock private AttackPatternRepository attackPatternRepository;
  @Mock private TagRepository tagRepository;
  @Mock private InjectorService injectorService;
  @Mock private OrganizationService organizationService;
  @Mock private InjectIndexCleanupService injectIndexCleanupService;
  @InjectMocks private InjectorContractService service;

  private Injector injector;
  private Organization publisherOrganization;
  private User sessionUser;

  @BeforeEach
  void setUp() {
    injector = new Injector();
    injector.setId("injector-1");
    injector.setName(PUBLISHER);
    injector.setType("openaev_nuclei");
    injector.setExternal(true);

    publisherOrganization = new Organization();
    publisherOrganization.setId("org-1");
    publisherOrganization.setName(PUBLISHER);

    sessionUser = new User();
    sessionUser.setId("user-1");
    sessionUser.setEmail("jane.doe@filigran.io");

    lenient().when(injectorService.injector("injector-1")).thenReturn(injector);
    lenient().when(domainService.upserts(any(), any())).thenReturn(new HashSet<>());
    lenient()
        .when(injectorContractRepository.save(any(InjectorContract.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(organizationService.findOrCreateByName(PUBLISHER))
        .thenReturn(publisherOrganization);
    lenient().when(userService.currentUser()).thenReturn(sessionUser);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateAsHuman() {
    OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", List.of()));
  }

  private static InjectorContractAddInput addInput(String externalId) {
    InjectorContractAddInput input = new InjectorContractAddInput();
    input.setId("contract-1");
    input.setExternalId(externalId);
    input.setInjectorId("injector-1");
    input.setContent("{}");
    input.setDomains(new HashSet<>());
    return input;
  }

  @Nested
  @DisplayName("Creation")
  class Creation {

    @Test
    @DisplayName("Machine sync (external id) is attributed to the publisher, not the session user")
    void given_externalIdAndRealSession_should_attributeToPublisher() {
      // The exact bug being fixed: the sync loop authenticates with a human token,
      // but the external contract id marks the creation as machine provenance.
      authenticateAsHuman();

      InjectorContract created = service.createNewInjectorContract(addInput("CVE-2026-0001"));

      assertThat(created.getAuthorOrganization()).isEqualTo(publisherOrganization);
      assertThat(created.getAuthorUser()).isNull();
    }

    @Test
    @DisplayName("Interactive creation (no external id) is attributed to the session user")
    void given_noExternalIdAndRealSession_should_attributeToSessionUser() {
      authenticateAsHuman();

      InjectorContract created = service.createNewInjectorContract(addInput(null));

      assertThat(created.getAuthorUser()).isEqualTo(sessionUser);
      assertThat(created.getAuthorOrganization()).isNull();
    }

    @Test
    @DisplayName("Anonymous machine creation without external id still goes to the publisher")
    void given_anonymousSession_should_attributeToPublisher() {
      // Legacy machine callers that predate external contract ids.
      InjectorContract created = service.createNewInjectorContract(addInput(null));

      assertThat(created.getAuthorOrganization()).isEqualTo(publisherOrganization);
      assertThat(created.getAuthorUser()).isNull();
    }

    @Test
    @DisplayName("External id on a non-external injector keeps the session user as author")
    void given_builtinInjector_should_attributeToSessionUser() {
      injector.setExternal(false);
      authenticateAsHuman();

      InjectorContract created = service.createNewInjectorContract(addInput("some-external-id"));

      assertThat(created.getAuthorUser()).isEqualTo(sessionUser);
      assertThat(created.getAuthorOrganization()).isNull();
    }
  }

  @Nested
  @DisplayName("Reconciliation on update")
  class Reconciliation {

    private InjectorContract existingContract(String externalId, User authorUser) {
      InjectorContract contract = new InjectorContract();
      contract.setId("contract-1");
      contract.setExternalId(externalId);
      contract.setAuthorUser(authorUser);
      contract.addInjector(injector);
      lenient()
          .when(injectorContractRepository.findByIdOrExternalId(anyString(), anyString()))
          .thenReturn(Optional.of(contract));
      lenient()
          .when(attackPatternService.findAllByInternalIdsThrowIfMissing(any()))
          .thenReturn(new ArrayList<>());
      return contract;
    }

    private static InjectorContractUpdateInput updateInput() {
      InjectorContractUpdateInput input = new InjectorContractUpdateInput();
      input.setContent("{}");
      input.setDomains(new HashSet<>());
      return input;
    }

    @Test
    @DisplayName("A synced contract mis-attributed to a user self-repairs to the publisher")
    void given_misattributedSyncedContract_should_reattributeToPublisher() {
      existingContract("CVE-2026-0001", sessionUser);

      InjectorContract updated = service.updateInjectorContract("contract-1", updateInput());

      assertThat(updated.getAuthorOrganization()).isEqualTo(publisherOrganization);
      assertThat(updated.getAuthorUser()).isNull();
    }

    @Test
    @DisplayName("An authorless legacy contract self-repairs to the publisher")
    void given_authorlessContract_should_attributeToPublisher() {
      existingContract("CVE-2026-0001", null);

      InjectorContract updated = service.updateInjectorContract("contract-1", updateInput());

      assertThat(updated.getAuthorOrganization()).isEqualTo(publisherOrganization);
    }

    @Test
    @DisplayName("A user-authored contract without external id is never touched")
    void given_userAuthoredInteractiveContract_should_keepAuthor() {
      InjectorContract contract = existingContract(null, sessionUser);

      InjectorContract updated = service.updateInjectorContract("contract-1", updateInput());

      assertThat(updated.getAuthorUser()).isEqualTo(sessionUser);
      assertThat(updated.getAuthorOrganization()).isNull();
      verify(injectorContractRepository).save(contract);
    }
  }
}
