package io.openaev.rest.injector_contract;

import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.DomainRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.helper.SupportedLanguage;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.ContractDef;
import io.openaev.injector_contract.fields.ContractText;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractDomainDTO;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.helpers.UserTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.sql.BatchUpdateException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.javacrumbs.jsonunit.core.Option;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Injector Contract API tests")
public class InjectorContractApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager em;
  @Autowired private ObjectMapper mapper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private VulnerabilityComposer vulnerabilityComposer;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorContractService injectorContractService;
  @Autowired private DomainComposer domainComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainRepository domainRepository;
  @Autowired private TagComposer tagComposer;

  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private GrantComposer grantComposer;

  @Autowired private UserTestHelper userTestHelper;

  @BeforeEach
  public void setup() {
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    payloadComposer.reset();
    vulnerabilityComposer.reset();
    userComposer.reset();
    tenantGroupComposer.reset();
    tenantRoleComposer.reset();
    grantComposer.reset();
    domainComposer.reset();
    tagComposer.reset();
  }

  @Nested
  @DisplayName("With internal ID")
  class WithInternalId {

    @Test
    @DisplayName("When internal ID is empty, fetching by internal ID fails with NOT FOUND")
    void whenExternalIdIsNull_FetchingByExternalIdFailsWithBadRequest() throws Exception {
      mvc.perform(
              get(INJECTOR_CONTRACT_URL + "//")
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Nested
    @DisplayName("When injector contract already exists")
    class WhenInjectorContractAlreadyExists {

      private void createStaticInjectorContract() {
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .persist();
        em.flush();
        em.clear();
      }

      @BeforeEach
      void beforeEach() {
        createStaticInjectorContract();
      }

      @Test
      @DisplayName("Updating attack pattern mappings succeeds")
      void updatingAttackPatternMappingsSucceeds() throws Exception {
        for (int i = 0; i < 3; ++i) {
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist();
        }
        em.flush();
        em.clear();

        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setAttackPatternsIds(
            attackPatternComposer.generatedItems.stream().map(AttackPattern::getId).toList());

        mvc.perform(
                put(INJECTOR_CONTRACT_URL
                        + "/"
                        + injectorContractComposer.generatedItems.getFirst().getId()
                        + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName(
          "Updating attack pattern mappings with non-existing attack patterns fail with NOT FOUND")
      void updatingAttackPatternMappingsWithNonExistingAttackPatternsFailWithNotFound()
          throws Exception {
        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setAttackPatternsIds(List.of(UUID.randomUUID().toString()));

        mvc.perform(
                put(INJECTOR_CONTRACT_URL
                        + "/"
                        + injectorContractComposer.generatedItems.getFirst().getId()
                        + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Fetching by internal ID succeeds")
      void fetchByExternalIdSucceeds() throws Exception {
        InjectorContract ic = injectorContractComposer.generatedItems.getFirst();
        String body =
            mvc.perform(
                    get(INJECTOR_CONTRACT_URL
                            + "/"
                            + injectorContractComposer.generatedItems.getFirst().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(body)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(mapper.writeValueAsString(ic));
      }

      @Nested
      @DisplayName("When deleting an injector contract")
      class WhenDeletingAnInjectorContract {

        @Test
        @DisplayName("Deleting a non custom contract fails")
        void deleteNonCustomContractFails() throws Exception {
          mvc.perform(
                  delete(
                          INJECTOR_CONTRACT_URL
                              + "/"
                              + injectorContractComposer.generatedItems.getFirst().getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deleting custom contract succeeds")
        void deleteCustomContractSucceeds() throws Exception {
          String customContractExternalId = "custom contract internal id";

          InjectorContract ic =
              InjectorContractFixture.createDefaultInjectorContractWithExternalId(
                  customContractExternalId);
          ic.setCustom(true);
          InjectorContract customContract =
              injectorContractComposer
                  .forInjectorContract(ic)
                  .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                  .persist()
                  .get();
          em.flush();
          em.clear();

          mvc.perform(
                  delete(INJECTOR_CONTRACT_URL + "/" + customContract.getExternalId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());
        }
      }

      @Test
      @DisplayName("Updating contract succeeds")
      void updateContractSucceeds() throws Exception {
        VulnerabilityComposer.Composer vulnWrapper =
            vulnerabilityComposer
                .forVulnerability(
                    VulnerabilityFixture.createVulnerabilityInput(
                        VulnerabilityFixture.getRandomExternalVulnerabilityId()))
                .persist();
        AttackPatternComposer.Composer attackPatternWrapper =
            attackPatternComposer
                .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
                .persist();
        em.flush();
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();

        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");
        input.setVulnerabilityIds(List.of(vulnWrapper.get().getId()));
        input.setAttackPatternsIds(List.of(attackPatternWrapper.get().getId()));
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));

        String response =
            mvc.perform(
                    put(INJECTOR_CONTRACT_URL
                            + "/"
                            + injectorContractComposer.generatedItems.getFirst().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .node("injector_contract_attack_patterns")
            .isEqualTo(mapper.writeValueAsString(List.of(attackPatternWrapper.get().getId())));
        assertThatJson(response)
            .node("injector_contract_vulnerabilities")
            .isEqualTo(mapper.writeValueAsString(List.of(vulnWrapper.get().getId())));
      }

      @Test
      @DisplayName("Updating contract succeeds with external vuln IDs")
      void updateContractWithExtVulnIdsSucceeds() throws Exception {
        VulnerabilityComposer.Composer vulnWrapper =
            vulnerabilityComposer
                .forVulnerability(
                    VulnerabilityFixture.createVulnerabilityInput(
                        VulnerabilityFixture.getRandomExternalVulnerabilityId()))
                .persist();
        VulnerabilityComposer.Composer otherVulnWrapper =
            vulnerabilityComposer
                .forVulnerability(
                    VulnerabilityFixture.createVulnerabilityInput(
                        VulnerabilityFixture.getRandomExternalVulnerabilityId()))
                .persist();
        AttackPatternComposer.Composer attackPatternWrapper =
            attackPatternComposer
                .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
                .persist();
        em.flush();

        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();

        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");
        input.setVulnerabilityIds(List.of(vulnWrapper.get().getId()));
        input.setVulnerabilityExternalIds(List.of(otherVulnWrapper.get().getExternalId()));
        input.setAttackPatternsIds(List.of(attackPatternWrapper.get().getId()));
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));

        String response =
            mvc.perform(
                    put(INJECTOR_CONTRACT_URL
                            + "/"
                            + injectorContractComposer.generatedItems.getFirst().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .node("injector_contract_attack_patterns")
            .isEqualTo(mapper.writeValueAsString(List.of(attackPatternWrapper.get().getId())));
        // external iDs should override internal IDs for consistency
        assertThatJson(response)
            .node("injector_contract_vulnerabilities")
            .isEqualTo(mapper.writeValueAsString(List.of(otherVulnWrapper.get().getId())));
      }
    }

    @Nested
    @DisplayName("When injector contract does not already exists")
    class WhenInjectorContractDoesNotAlreadyExists {

      private final String injectorContractInternalId = UUID.randomUUID().toString();

      @Test
      @DisplayName("Without attack patterns, creating contract succeeds")
      void createContractSucceeds() throws Exception {
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(
                String.format(
                    """
                        {
                          "convertedContent":null,"listened":true,"injector_contract_id":"%s",
                          "injector_contract_external_id":null,
                          "injector_contract_labels":null,"injector_contract_manual":false,
                          "injector_contract_content":"{\\"fields\\":[]}",
                          "injector_contract_custom":true,"injector_contract_needs_executor":false,
                          "injector_contract_platforms":[],"injector_contract_payload":null,
                          "injector_contract_injectors":["49229430-b5b5-431f-ba5b-f36f599b0144"],
                          "injector_contract_injector_names":{"49229430-b5b5-431f-ba5b-f36f599b0144":"OpenAEV Implant"},
                          "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_domains":[],
                          "injector_contract_tags":[]
                        }
                        """,
                    injectorContractInternalId));
      }

      @Test
      @DisplayName("With missing attack patterns, creating contract fails with NOT FOUND")
      void withMissingAttackPatternsCreateContractFailsWithNOTFOUND() throws Exception {
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsIds(List.of(UUID.randomUUID().toString()));
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setContent("{\"fields\":[]}");

        mvc.perform(
                post(INJECTOR_CONTRACT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("With missing vulnerabilities, creating contract fails with NOT FOUND")
      void withMissingVulnerabilitiesCreateContractFailsWithNOTFOUND() throws Exception {
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setVulnerabilityIds(List.of(UUID.randomUUID().toString()));
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setContent("{\"fields\":[]}");

        mvc.perform(
                post(INJECTOR_CONTRACT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("With existing attack patterns by internal ID, creating contract succeeds")
      void withExistingAttackPatternsByInternalIdCreateContractSucceeds() throws Exception {
        for (int i = 0; i < 3; ++i) {
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist();
        }
        em.flush();
        em.clear();
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsIds(
            attackPatternComposer.generatedItems.stream().map(AttackPattern::getId).toList());
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(
                String.format(
                    """
                        {
                          "convertedContent":null,"listened":true,"injector_contract_id":"%s",
                          "injector_contract_external_id":null,
                          "injector_contract_labels":null,"injector_contract_manual":false,
                          "injector_contract_content":"{\\"fields\\":[]}",
                          "injector_contract_custom":true,"injector_contract_needs_executor":false,
                          "injector_contract_platforms":[],"injector_contract_payload":null,
                          "injector_contract_injectors":["49229430-b5b5-431f-ba5b-f36f599b0144"],
                          "injector_contract_injector_names":{"49229430-b5b5-431f-ba5b-f36f599b0144":"OpenAEV Implant"},
                          "injector_contract_attack_patterns":[%s],"injector_contract_vulnerabilities":[],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_domains":[],
                          "injector_contract_tags":[]
                        }
                        """,
                    injectorContractInternalId,
                    String.join(
                        ",",
                        attackPatternComposer.generatedItems.stream()
                            .map(ap -> String.format("\"" + ap.getId() + "\""))
                            .toList())));
      }

      @Test
      @DisplayName("With existing attack patterns by external ID, creating contract succeeds")
      void withExistingAttackPatternsByExternalIdCreateContractSucceeds() throws Exception {
        for (int i = 0; i < 3; ++i) {
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist();
        }
        em.flush();
        em.clear();

        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsExternalIds(
            attackPatternComposer.generatedItems.stream()
                .map(ap -> ap.getExternalId().toLowerCase())
                .toList());
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setContent("{\"fields\":[]}");
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(
                String.format(
                    """
                        {
                          "convertedContent":null,"listened":true,"injector_contract_id":"%s",
                          "injector_contract_external_id":null,
                          "injector_contract_labels":null,"injector_contract_manual":false,
                          "injector_contract_content":"{\\"fields\\":[]}",
                          "injector_contract_custom":true,"injector_contract_needs_executor":false,
                          "injector_contract_platforms":[],"injector_contract_payload":null,
                          "injector_contract_injectors":["49229430-b5b5-431f-ba5b-f36f599b0144"],
                          "injector_contract_injector_names":{"49229430-b5b5-431f-ba5b-f36f599b0144":"OpenAEV Implant"},
                          "injector_contract_attack_patterns":[%s],"injector_contract_vulnerabilities":[],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_domains":[],
                          "injector_contract_tags":[]
                        }
                        """,
                    injectorContractInternalId,
                    String.join(
                        ",",
                        attackPatternComposer.generatedItems.stream()
                            .map(ap -> String.format("\"" + ap.getId() + "\""))
                            .toList())));
      }

      @Test
      @DisplayName("With existing vulnerabilities, creating contract succeeds")
      void withExistingVulnerabilitiesCreateContractSucceeds() throws Exception {
        for (int i = 0; i < 3; ++i) {
          vulnerabilityComposer
              .forVulnerability(
                  VulnerabilityFixture.createVulnerabilityInput(
                      VulnerabilityFixture.getRandomExternalVulnerabilityId()))
              .persist();
        }
        em.flush();
        em.clear();
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setVulnerabilityIds(
            vulnerabilityComposer.generatedItems.stream().map(Vulnerability::getId).toList());
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(
                String.format(
                    """
                        {
                          "convertedContent":null,"listened":true,"injector_contract_id":"%s",
                          "injector_contract_external_id":null,
                          "injector_contract_labels":null,"injector_contract_manual":false,
                          "injector_contract_content":"{\\"fields\\":[]}",
                          "injector_contract_custom":true,"injector_contract_needs_executor":false,
                          "injector_contract_platforms":[],"injector_contract_payload":null,
                          "injector_contract_injectors":["49229430-b5b5-431f-ba5b-f36f599b0144"],
                          "injector_contract_injector_names":{"49229430-b5b5-431f-ba5b-f36f599b0144":"OpenAEV Implant"},
                          "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[%s],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_domains":[],
                          "injector_contract_tags":[]
                        }
                        """,
                    injectorContractInternalId,
                    String.join(
                        ",",
                        vulnerabilityComposer.generatedItems.stream()
                            .map(vuln -> String.format("\"" + vuln.getId() + "\""))
                            .toList())));
      }

      @Test
      @DisplayName("With existing vulnerabilities by external ID, creating contract succeeds")
      void withExistingVulnerabilitiesByExternalIdCreateContractSucceeds() throws Exception {
        for (int i = 0; i < 3; ++i) {
          vulnerabilityComposer
              .forVulnerability(
                  VulnerabilityFixture.createVulnerabilityInput(
                      VulnerabilityFixture.getRandomExternalVulnerabilityId()))
              .persist();
        }
        em.flush();
        em.clear();
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setVulnerabilityExternalIds(
            // force converting the ids to lower case; it must work in case-insensitive mode
            vulnerabilityComposer.generatedItems.stream()
                .map(vuln -> vuln.getExternalId().toLowerCase())
                .toList());
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_ARRAY_ORDER)
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(
                String.format(
                    """
                        {
                          "convertedContent":null,"listened":true,"injector_contract_id":"%s",
                          "injector_contract_external_id":null,
                          "injector_contract_labels":null,"injector_contract_manual":false,
                          "injector_contract_content":"{\\"fields\\":[]}",
                          "injector_contract_custom":true,"injector_contract_needs_executor":false,
                          "injector_contract_platforms":[],"injector_contract_payload":null,
                          "injector_contract_injectors":["49229430-b5b5-431f-ba5b-f36f599b0144"],
                          "injector_contract_injector_names":{"49229430-b5b5-431f-ba5b-f36f599b0144":"OpenAEV Implant"},
                          "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[%s],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_domains":[],
                          "injector_contract_tags":[]
                        }
                        """,
                    injectorContractInternalId,
                    String.join(
                        ",",
                        vulnerabilityComposer.generatedItems.stream()
                            .map(vuln -> String.format("\"" + vuln.getId() + "\""))
                            .toList())));
      }

      @Test
      @DisplayName("Fetching by internal ID fails with NOT FOUND")
      void fetchByInternalIdFailsWithNotFound() throws Exception {
        mvc.perform(
                get(INJECTOR_CONTRACT_URL + "/" + injectorContractInternalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Updating attack pattern mappings fails with NOT FOUND")
      void updatingAttackPatternMappingsFailsWithNotFound() throws Exception {
        for (int i = 0; i < 3; ++i) {
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist();
        }
        em.flush();
        em.clear();

        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setAttackPatternsIds(
            attackPatternComposer.generatedItems.stream().map(AttackPattern::getId).toList());

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + injectorContractInternalId + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Deleting contract fails with NOT FOUND")
      void deleteContractFailsWithNotFound() throws Exception {
        mvc.perform(
                delete(INJECTOR_CONTRACT_URL + "/" + injectorContractInternalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Updating contract fails with NOT FOUND")
      void updateContractFailsWithNotFound() throws Exception {
        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + injectorContractInternalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }
    }
  }

  @Nested
  @DisplayName("With external ID")
  class WithExternalId {

    private final String externalId = "contract external id";

    @Test
    @DisplayName("When external ID is empty, fetching by External ID fails with NOT FOUND")
    void whenExternalIdIsNull_FetchingByExternalIdFailsWithBadRequest() throws Exception {
      mvc.perform(
              get(INJECTOR_CONTRACT_URL + "//")
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Nested
    @DisplayName("When injector contract already exists")
    class WhenInjectorContractAlreadyExists {

      private void createStaticInjectorContract() {
        injectorContractComposer
            .forInjectorContract(
                InjectorContractFixture.createDefaultInjectorContractWithExternalId(externalId))
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .persist();
        em.flush();
        em.clear();
      }

      @BeforeEach
      void beforeEach() {
        createStaticInjectorContract();
      }

      @Test
      @DisplayName("Creating contract with same external ID conflicts in the database")
      void createContractFailsWithConflict() {
        assertThatThrownBy(this::createStaticInjectorContract)
            .hasCauseInstanceOf(BatchUpdateException.class)
            .cause()
            .hasMessageContaining("injectors_contracts_injector_contract_external_id_key");
      }

      @Test
      @DisplayName("Updating attack pattern mappings succeeds")
      void updatingAttackPatternMappingsSucceeds() throws Exception {
        for (int i = 0; i < 3; ++i) {
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist();
        }
        em.flush();
        em.clear();

        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setAttackPatternsIds(
            attackPatternComposer.generatedItems.stream().map(AttackPattern::getId).toList());

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + externalId + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName(
          "Updating attack pattern mappings with non-existing attack patterns fail with NOT FOUND")
      void updatingAttackPatternMappingsWithNonExistingAttackPatternsFailWithNotFound()
          throws Exception {
        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setAttackPatternsIds(List.of(UUID.randomUUID().toString()));

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + externalId + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Fetching by External ID succeeds")
      void fetchByExternalIdSucceeds() throws Exception {
        InjectorContract ic = injectorContractComposer.generatedItems.getFirst();
        String body =
            mvc.perform(
                    get(INJECTOR_CONTRACT_URL + "/" + externalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(body)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(mapper.writeValueAsString(ic));
      }

      @Nested
      @DisplayName("When deleting an injector contract")
      class WhenDeletingAnInjectorContract {

        @Test
        @DisplayName("Deleting a non custom contract fails")
        void deleteNonCustomContractFails() throws Exception {
          mvc.perform(
                  delete(INJECTOR_CONTRACT_URL + "/" + externalId)
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deleting custom contract succeeds")
        void deleteCustomContractSucceeds() throws Exception {
          String customContractExternalId = "custom contract external id";

          InjectorContract ic =
              InjectorContractFixture.createDefaultInjectorContractWithExternalId(
                  customContractExternalId);
          ic.setCustom(true);
          InjectorContract customContract =
              injectorContractComposer
                  .forInjectorContract(ic)
                  .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                  .persist()
                  .get();
          em.flush();
          em.clear();

          mvc.perform(
                  delete(INJECTOR_CONTRACT_URL + "/" + customContract.getExternalId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());
        }
      }

      @Test
      @DisplayName("Updating contract succeeds")
      void updateContractSucceeds() throws Exception {
        VulnerabilityComposer.Composer vulnWrapper =
            vulnerabilityComposer
                .forVulnerability(
                    VulnerabilityFixture.createVulnerabilityInput(
                        VulnerabilityFixture.getRandomExternalVulnerabilityId()))
                .persist();
        AttackPatternComposer.Composer attackPatternWrapper =
            attackPatternComposer
                .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
                .persist();
        em.flush();
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();

        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");
        input.setVulnerabilityIds(List.of(vulnWrapper.get().getId()));
        input.setAttackPatternsIds(List.of(attackPatternWrapper.get().getId()));
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));

        String response =
            mvc.perform(
                    put(INJECTOR_CONTRACT_URL + "/" + externalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .node("injector_contract_attack_patterns")
            .isEqualTo(mapper.writeValueAsString(List.of(attackPatternWrapper.get().getId())));
        assertThatJson(response)
            .node("injector_contract_vulnerabilities")
            .isEqualTo(mapper.writeValueAsString(List.of(vulnWrapper.get().getId())));
      }
    }

    @Nested
    @DisplayName("When injector contract does not already exists")
    class WhenInjectorContractDoesNotAlreadyExists {

      @Test
      @DisplayName("Creating contract succeeds from injector payload type")
      void createContractSucceedsFromInjectorPayloadType() throws Exception {
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();
        String newId = UUID.randomUUID().toString();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(newId);
        input.setExternalId(externalId);
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(
                String.format(
                    """
                                  {
                                    "convertedContent":null,"listened":true,"injector_contract_id":"%s",
                                    "injector_contract_external_id":"contract external id",
                                    "injector_contract_labels":null,"injector_contract_manual":false,
                                    "injector_contract_content":"{\\"fields\\":[]}",
                                    "injector_contract_custom":true,"injector_contract_needs_executor":false,
                                    "injector_contract_platforms":[],"injector_contract_payload":null,
                                    "injector_contract_injectors":["49229430-b5b5-431f-ba5b-f36f599b0144"],
                                    "injector_contract_injector_names":{"49229430-b5b5-431f-ba5b-f36f599b0144":"OpenAEV Implant"},
                                    "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[],
                                    "injector_contract_atomic_testing":true,
                                    "injector_contract_import_available":false,"injector_contract_arch":null,
                                    "injector_contract_injector_type":"openaev_implant",
                                    "injector_contract_domains":[],
                                    "injector_contract_tags":[]
                                  }""",
                    newId));
      }

      @Test
      @DisplayName("Creating contract succeeds")
      void createContractSucceeds() throws Exception {
        Domain domain = DomainFixture.getRandomDomain();
        Set<Domain> domains = domainComposer.forDomain(domain).persist().getSet();
        String newId = UUID.randomUUID().toString();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(newId);
        input.setExternalId(externalId);
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setInjectorId(injectorFixture.getWellKnownEmailInjector(false).getId());
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_EXTRA_FIELDS)
            .isEqualTo(
                String.format(
                    """
                                    {
                                      "convertedContent":null,"listened":true,"injector_contract_id":"%s",
                                      "injector_contract_external_id":"contract external id",
                                      "injector_contract_labels":null,"injector_contract_manual":false,
                                      "injector_contract_content":"{\\"fields\\":[]}",
                                      "injector_contract_custom":true,"injector_contract_needs_executor":false,
                                      "injector_contract_platforms":[],"injector_contract_payload":null,
                                      "injector_contract_injectors":["41b4dd55-5bd1-4614-98cd-9e3770753306"],
                                      "injector_contract_injector_names":{"41b4dd55-5bd1-4614-98cd-9e3770753306":"Email"},
                                      "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[],
                                      "injector_contract_atomic_testing":true,
                                      "injector_contract_import_available":false,"injector_contract_arch":null,
                                      "injector_contract_injector_type":"openaev_email",
                                      "injector_contract_domains":["%s"],
                                      "injector_contract_tags": []
                                    }""",
                    newId, domain.getId()));
      }

      @Test
      @DisplayName("Fetching by External ID fails with NOT FOUND")
      void fetchByExternalIdFailsWithNotFound() throws Exception {
        mvc.perform(
                get(INJECTOR_CONTRACT_URL + "/" + externalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Updating attack pattern mappings fails with NOT FOUND")
      void updatingAttackPatternMappingsFailsWithNotFound() throws Exception {
        for (int i = 0; i < 3; ++i) {
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist();
        }
        em.flush();
        em.clear();

        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setAttackPatternsIds(
            attackPatternComposer.generatedItems.stream().map(AttackPattern::getId).toList());

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + externalId + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Deleting contract fails with NOT FOUND")
      void deleteContractFailsWithNotFound() throws Exception {
        mvc.perform(
                delete(INJECTOR_CONTRACT_URL + "/" + externalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Updating contract fails with NOT FOUND")
      void updateContractFailsWithNotFound() throws Exception {
        Set<Domain> domains =
            domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();
        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setDomains(
            domains.stream()
                .map(InjectorContractDomainDTO::fromDomain)
                .collect(Collectors.toSet()));
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + externalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }
    }
  }

  @Nested
  @DisplayName("Injector Contract search tests")
  class InjectorContractSearchTests {

    private void createStaticInjectorContract() {
      injectorContractComposer
          .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
          .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
          .persist();
      em.flush();
      em.clear();
    }

    @BeforeEach
    void setUp() {
      for (int i = 0; i < 3; ++i) {
        createStaticInjectorContract();
      }
    }

    @Test
    @DisplayName("With classic SearchPaginationInput, search returns expected items")
    void WithClassicSearchPaginationInput() throws Exception {
      SearchPaginationInput input =
          PaginationFixture.simpleSearchWithAndOperator(
              "injector_contract_injectors",
              injectorFixture.getWellKnownOaevImplantInjector().getId(),
              Filters.FilterOperator.contains);

      String response =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .whenIgnoringPaths("content[*].injector_contract_updated_at")
          .when(Option.IGNORING_ARRAY_ORDER)
          .node("content")
          .isArray()
          .isEqualTo(
              mapper.writeValueAsString(
                  injectorContractComposer.generatedItems.stream()
                      .map(InjectorContractFullOutput::fromInjectorContract)));
    }

    @Test
    @DisplayName(
        "With SearchPaginationWithSerialisationOptionsInput and ignore content option is set, search returns expected items with no content")
    void WithSearchPaginationWithSerialisationOptionsInput() throws Exception {
      InjectorContractSearchPaginationInput input =
          PaginationFixture.optionedSearchWithAndOperator(
              "injector_contract_injectors",
              injectorFixture.getWellKnownOaevImplantInjector().getId(),
              Filters.FilterOperator.contains);
      input.setIncludeFullDetails(false);

      String response =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .whenIgnoringPaths("content[*].injector_contract_updated_at")
          .when(Option.IGNORING_ARRAY_ORDER)
          .node("content")
          .isArray()
          .isEqualTo(
              mapper.writeValueAsString(
                  injectorContractComposer.generatedItems.stream()
                      .map(InjectorContractBaseOutput::fromInjectorContract)));
    }
  }

  @Nested
  @DisplayName("Injector Contract search tests with different user types for RBAC")
  class InjectorContractSearchTestsForDifferentUsers {

    // Enum for user types to make parameterized tests cleaner
    enum UserType {
      NO_GROUPS,
      ADMIN,
      WITH_BYPASS,
      WITH_ACCESS_THREAT_ARSENALS,
    }

    public UserComposer.Composer createTestUser(UserType userType, List<String> resourceIds) {
      return userTestHelper.createTestUser(
          UserTestHelper.UserType.valueOf(userType.name()), resourceIds);
    }

    private int preExistingContractsCount;
    private List<InjectorContract> injectorContractsCreated = new ArrayList<>();

    private void createStaticInjectorContract(boolean addPayload) {
      InjectorContractComposer.Composer icComposer =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()).persist())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector());
      if (addPayload) {
        icComposer.withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()));
      }
      InjectorContract ic = icComposer.persist().get();
      injectorContractsCreated.add(ic);
      em.flush();
      em.clear();
    }

    @BeforeEach
    void setUp() {
      injectorContractsCreated.clear();
      preExistingContractsCount = (int) injectorContractRepository.count();
      for (int i = 0; i < 3; ++i) {
        createStaticInjectorContract(i == 0);
      }
    }

    // Method source for parameterized tests
    private static Stream<Arguments> userTestCases() {
      return Stream.of(
          Arguments.of(
              "User with no groups",
              UserType.NO_GROUPS,
              0, // number of granted threat Arsenal action
              false // shouldSeeAllContracts
              ),
          Arguments.of(
              "Admin user",
              UserType.ADMIN,
              0, // number of granted threat Arsenal action
              true // Admin sees all
              ),
          Arguments.of(
              "User with BYPASS capability",
              UserType.WITH_BYPASS,
              0, // number of granted threat Arsenal action
              true // BYPASS users should see all
              ),
          Arguments.of(
              "User with ACCESS_THREAT_ARSENALS capability",
              UserType.WITH_ACCESS_THREAT_ARSENALS,
              0, // number of granted threat Arsenal action
              true // ACCESS_THREAT_ARSENALS users should see all actions
              ),
          Arguments.of(
              "User with OBSERVER grant on threat arsenal",
              UserType.NO_GROUPS,
              2, // number of granted threat Arsenal action
              false // users should see granted actions only
              ));
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("userTestCases")
    @DisplayName("GET /injector-contracts - Test access control for different user types")
    void testGetInjectContracts(
        String testCase, UserType userType, int grantedActionNumber, boolean shouldSeeAllContracts)
        throws Exception {

      List<String> grantedResourceIds = new ArrayList<>();
      for (int i = 0; i < grantedActionNumber; i++) {
        grantedResourceIds.add(injectorContractsCreated.get(i).getId());
      }
      // Create test user based on type
      User testUser = createTestUser(userType, grantedResourceIds).persist().get();
      Authentication auth = buildAuthenticationToken(testUser);

      // Perform the request with the test user context
      ResultActions result =
          mvc.perform(
                  get(INJECTOR_CONTRACT_URL)
                      .with(authentication(auth))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andDo(print())
              .andExpect(status().is(HttpStatus.SC_OK));

      // Verify the response based on user permissions
      if (shouldSeeAllContracts) {
        // Admin, BYPASS, ACCESS_THREAT_ARSENALS users see everything
        result.andExpect(jsonPath("$", hasSize(equalTo(preExistingContractsCount + 3))));
      } else {
        // User with no groups only sees contracts granted
        result.andExpect(jsonPath("$", hasSize(equalTo(grantedActionNumber))));
      }
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("userTestCases")
    @DisplayName(
        "POST /injector-contracts/search without full details - Test search access control for different user types")
    void testSearchInjectorContracts(
        String testCase, UserType userType, int grantedActionNumber, boolean shouldSeeAllContracts)
        throws Exception {

      List<String> grantedResourceIds = new ArrayList<>();
      for (int i = 0; i < grantedActionNumber; i++) {
        grantedResourceIds.add(injectorContractsCreated.get(i).getId());
      }

      // Create test user based on type
      User testUser = createTestUser(userType, grantedResourceIds).persist().get();

      Authentication auth = buildAuthenticationToken(testUser);

      // Create search input
      InjectorContractSearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setIncludeFullDetails(false);

      ResultActions result =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .with(authentication(auth))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(searchPaginationInput))
                      .with(csrf()))
              .andExpect(status().is(HttpStatus.SC_OK));

      // Verify pagination response
      result.andExpect(jsonPath("$.totalElements").exists());
      result.andExpect(jsonPath("$.content").isArray());

      if (shouldSeeAllContracts) {
        // Should see at least contracts without payload
        result.andExpect(jsonPath("$.totalElements", equalTo(preExistingContractsCount + 3)));
      } else {
        // Should only see contracts without payload
        result.andExpect(
            jsonPath("$.totalElements", equalTo(preExistingContractsCount + grantedActionNumber)));
      }
    }

    //
    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("userTestCases")
    @DisplayName(
        "POST /injector-contracts/search with full details - Test search access control for different user types")
    void testSearchInjectorContractsWithFullDetails(
        String testCase, UserType userType, int grantedActionNumber, boolean shouldSeeAllContracts)
        throws Exception {

      List<String> grantedResourceIds = new ArrayList<>();
      for (int i = 0; i < grantedActionNumber; i++) {
        grantedResourceIds.add(injectorContractsCreated.get(i).getId());
      }

      // Create test user based on type
      User testUser = createTestUser(userType, grantedResourceIds).persist().get();

      Authentication auth = buildAuthenticationToken(testUser);

      // Create search input
      InjectorContractSearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setIncludeFullDetails(true);

      ResultActions result =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .with(authentication(auth))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(searchPaginationInput))
                      .with(csrf()))
              .andExpect(status().is(HttpStatus.SC_OK));

      // Verify pagination response with full details
      result.andExpect(jsonPath("$.totalElements").exists());
      result.andExpect(jsonPath("$.content").isArray());

      // When full details are requested, verify additional fields are present
      if ((shouldSeeAllContracts || grantedActionNumber > 0)
          && result.andReturn().getResponse().getContentAsString().contains("content")) {
        result.andExpect(jsonPath("$.content[0].injector_contract_content").exists());
      }

      if (shouldSeeAllContracts) {
        result.andExpect(jsonPath("$.totalElements", equalTo(preExistingContractsCount + 3)));
      } else {
        // Should only see contracts without payload
        result.andExpect(
            jsonPath("$.totalElements", equalTo(preExistingContractsCount + grantedActionNumber)));
      }
    }

    @Test
    @DisplayName(
        "A contract with one attack pattern and several tags returns that attack pattern only once")
    void searchDoesNotDuplicateAttackPatternsWhenContractHasSeveralTags() throws Exception {
      // buildCommonInjectorContractContext aggregates domains/attackPatterns/tags via parallel
      // LEFT JOINs under a single GROUP BY that covers none of them; more than one tag on the
      // contract used to fan out the cartesian product and duplicate the single attack pattern.
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist();
      InjectorContract taggedContract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
              .withAttackPattern(attackPatternWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("tag-one")).persist())
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("tag-two")).persist())
              .persist()
              .get();
      em.flush();
      em.clear();

      InjectorContractSearchPaginationInput searchInput = new InjectorContractSearchPaginationInput();
      searchInput.setIncludeFullDetails(true);
      searchInput.setInjectorContractIdsToProcess(List.of(taggedContract.getId()));

      String response =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(searchInput))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .node("content[0].injector_contract_attack_patterns")
          .isArray()
          .isEqualTo(mapper.writeValueAsString(List.of(attackPatternWrapper.get().getId())));
    }
  }

  @Nested
  @DisplayName("When contracts are linked to security domains")
  class WhenContractsAreLinkedToDomains {
    @Test
    @DisplayName("It should aggregate counts correctly by domain category")
    void getDomainCountsReturnAggregation() throws Exception {
      domainRepository.deleteAll();
      em.flush();

      Set<Domain> endpointDomain =
          domainComposer.forDomain(PresetDomain.getEndpoint()).persist().getSet();
      Set<Domain> cloudDomain =
          domainComposer.forDomain(PresetDomain.getCloud()).persist().getSet();

      Injector validInjector = injectorFixture.getWellKnownOaevImplantInjector();

      InjectorContract contract1 = InjectorContractFixture.createDefaultInjectorContract();
      contract1.setId(UUID.randomUUID().toString());

      contract1.setDomains(new HashSet<>(endpointDomain));

      injectorContractComposer.forInjectorContract(contract1).withInjector(validInjector).persist();

      InjectorContract contract2 = InjectorContractFixture.createDefaultInjectorContract();
      contract2.setId(UUID.randomUUID().toString());

      contract2.setDomains(new HashSet<>(endpointDomain));

      injectorContractComposer.forInjectorContract(contract2).withInjector(validInjector).persist();

      InjectorContract contract3 = InjectorContractFixture.createDefaultInjectorContract();
      contract3.setId(UUID.randomUUID().toString());

      contract3.setDomains(new HashSet<>(cloudDomain));

      injectorContractComposer.forInjectorContract(contract3).withInjector(validInjector).persist();

      InjectorContractSearchPaginationInput input = new InjectorContractSearchPaginationInput();

      String response =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/domain-counts")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .when(Option.IGNORING_EXTRA_ARRAY_ITEMS, Option.IGNORING_ARRAY_ORDER)
          .isEqualTo(
              String.format(
                  """
            [
              {
                "domain": "%s",
                "count": 2
              },
              {
                "domain": "%s",
                "count": 1
              }
            ]
            """,
                  endpointDomain.iterator().next().getId(), cloudDomain.iterator().next().getId()));
    }
  }

  @Nested
  @DisplayName("Builtin contract registration (applyBuiltinContractData)")
  class BuiltinContractRegistration {

    private Contract buildSourceContract(String id, List<String> attackPatternExternalIds) {
      ContractConfig config =
          new ContractConfig(
              "test-type",
              Map.of(SupportedLanguage.en, "Test Injector"),
              "#000000",
              "#FFFFFF",
              null);
      Contract contract =
          Contract.executableContract(
              config,
              id,
              Map.of(SupportedLanguage.en, "Test Contract"),
              ContractDef.contractBuilder()
                  .mandatory(ContractText.textField("field1", "Field 1"))
                  .build(),
              List.of(Endpoint.PLATFORM_TYPE.Generic),
              false,
              Set.of());
      attackPatternExternalIds.forEach(contract::addAttackPattern);
      return contract;
    }

    @Test
    @DisplayName(
        "given source contract with attack pattern external IDs — should resolve and set attack patterns from DB")
    void givenSourceWithAttackPatterns_shouldResolveAndSetAttackPatterns() {
      // -- ARRANGE --
      Injector injector = injectorFixture.getWellKnownOaevImplantInjector();

      AttackPattern ap1 =
          attackPatternComposer
              .forAttackPattern(
                  AttackPatternFixture.createAttackPatternsWithExternalId("T1566.001"))
              .persist()
              .get();
      AttackPattern ap2 =
          attackPatternComposer
              .forAttackPattern(
                  AttackPatternFixture.createAttackPatternsWithExternalId("T1059.001"))
              .persist()
              .get();
      em.flush();

      String contractId = UUID.randomUUID().toString();
      Contract source = buildSourceContract(contractId, List.of("T1566.001", "T1059.001"));

      // -- ACT --
      InjectorContract result =
          injectorContractService.createBuiltinInjectorContract(source, injector, true);

      // -- ASSERT --
      assertThat(result.getAttackPatterns())
          .extracting(AttackPattern::getExternalId)
          .containsExactlyInAnyOrder("T1566.001", "T1059.001");
    }

    @Test
    @DisplayName(
        "given ObjectMapper that fails to serialize — should throw IllegalStateException wrapping cause")
    void givenSerializationFailure_shouldThrowIllegalStateException() throws Exception {
      // -- ARRANGE --
      Injector injector = injectorFixture.getWellKnownOaevImplantInjector();

      String contractId = UUID.randomUUID().toString();
      Contract source = buildSourceContract(contractId, List.of());

      // Temporarily replace the mapper in the service with a spy that throws
      ObjectMapper originalMapper =
          (ObjectMapper)
              org.springframework.test.util.ReflectionTestUtils.getField(
                  injectorContractService, "mapper");
      ObjectMapper spyMapper = org.mockito.Mockito.spy(originalMapper);
      org.mockito.Mockito.doThrow(
              new com.fasterxml.jackson.core.JsonProcessingException("Simulated failure") {})
          .when(spyMapper)
          .writeValueAsString(source);
      org.springframework.test.util.ReflectionTestUtils.setField(
          injectorContractService, "mapper", spyMapper);

      try {
        // -- ACT & ASSERT --
        assertThatThrownBy(
                () -> injectorContractService.createBuiltinInjectorContract(source, injector, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to serialize contract content for: " + contractId)
            .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
      } finally {
        // Restore the original mapper
        org.springframework.test.util.ReflectionTestUtils.setField(
            injectorContractService, "mapper", originalMapper);
      }
    }
  }
}
