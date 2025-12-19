package io.openaev.rest.injector_contract;

import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.VulnerabilityFixture;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.VulnerabilityComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.BatchUpdateException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.javacrumbs.jsonunit.core.Option;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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
  @Autowired private DomainComposer domainComposer;
  @Autowired private PayloadComposer payloadComposer;

  @Autowired private UserComposer userComposer;
  @Autowired private GroupComposer groupComposer;
  @Autowired private RoleComposer roleComposer;
  @Autowired private GrantComposer grantComposer;

  @BeforeEach
  public void setup() {
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    payloadComposer.reset();
    vulnerabilityComposer.reset();
    userComposer.reset();
    groupComposer.reset();
    roleComposer.reset();
    grantComposer.reset();
  }

  @Nested
  @DisplayName("With internal ID")
  class WithInternalId {

    @Test
    @DisplayName("When internal ID is empty, fetching by internal ID fails with NOT FOUND")
    void whenExternalIdIsNull_FetchingByExternalIdFailsWithBadRequest() throws Exception {
      mvc.perform(get(INJECTOR_CONTRACT_URL + "//").contentType(MediaType.APPLICATION_JSON))
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
                    .content(mapper.writeValueAsString(input)))
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
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Updating vulnerability mappings succeeds")
      void updatingVulnerabilitiesMappingsSucceeds() throws Exception {
        for (int i = 0; i < 3; ++i) {
          vulnerabilityComposer
              .forVulnerability(
                  VulnerabilityFixture.createVulnerabilityInput(
                      VulnerabilityFixture.getRandomExternalVulnerabilityId()))
              .persist();
        }
        em.flush();
        em.clear();

        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setVulnerabilityIds(
            vulnerabilityComposer.generatedItems.stream().map(Vulnerability::getId).toList());

        mvc.perform(
                put(INJECTOR_CONTRACT_URL
                        + "/"
                        + injectorContractComposer.generatedItems.getFirst().getId()
                        + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName(
          "Updating vulnerability mappings with non-existing vulnerabilities fail with NOT FOUND")
      void updatingVulnerabilitiesMappingsWithNonExistingVulnerabilitiesFailWithNotFound()
          throws Exception {
        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setVulnerabilityIds(List.of(UUID.randomUUID().toString()));

        mvc.perform(
                put(INJECTOR_CONTRACT_URL
                        + "/"
                        + injectorContractComposer.generatedItems.getFirst().getId()
                        + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
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
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(body)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .isEqualTo(mapper.writeValueAsString(ic));
      }

      @Nested
      @DisplayName("When deleting an injector contract")
      class WhenDeletingAnInjectorContract {

        @Test
        @DisplayName("Deleting a non custom contract fails")
        void deleteNonCustomContractFails() {
          assertThatThrownBy(
                  () ->
                      mvc.perform(
                              delete(
                                      INJECTOR_CONTRACT_URL
                                          + "/"
                                          + injectorContractComposer
                                              .generatedItems
                                              .getFirst()
                                              .getId())
                                  .contentType(MediaType.APPLICATION_JSON))
                          .andReturn())
              .hasCauseInstanceOf(IllegalArgumentException.class)
              .hasMessageEndingWith(
                  "This injector contract can't be removed because is not a custom one: "
                      + injectorContractComposer.generatedItems.getFirst().getId());
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
                      .contentType(MediaType.APPLICATION_JSON))
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
        Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");
        input.setVulnerabilityIds(List.of(vulnWrapper.get().getId()));
        input.setAttackPatternsIds(List.of(attackPatternWrapper.get().getId()));
        input.setDomains(domains);

        String response =
            mvc.perform(
                    put(INJECTOR_CONTRACT_URL
                            + "/"
                            + injectorContractComposer.generatedItems.getFirst().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
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
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();

        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");
        input.setVulnerabilityIds(List.of(vulnWrapper.get().getId()));
        input.setVulnerabilityExternalIds(List.of(otherVulnWrapper.get().getExternalId()));
        input.setAttackPatternsIds(List.of(attackPatternWrapper.get().getId()));
        input.setDomains(domains);

        String response =
            mvc.perform(
                    put(INJECTOR_CONTRACT_URL
                            + "/"
                            + injectorContractComposer.generatedItems.getFirst().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
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
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(domains);
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
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
                          "injector_contract_injector":"49229430-b5b5-431f-ba5b-f36f599b0144",
                          "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_injector_type_name":"OpenAEV Implant",
                          "injector_contract_domains":[]
                        }
                        """,
                    injectorContractInternalId));
      }

      @Test
      @DisplayName("With missing attack patterns, creating contract fails with NOT FOUND")
      void withMissingAttackPatternsCreateContractFailsWithNOTFOUND() throws Exception {
        Set<Domain> domains =
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsIds(List.of(UUID.randomUUID().toString()));
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(domains);
        input.setContent("{\"fields\":[]}");

        mvc.perform(
                post(INJECTOR_CONTRACT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("With missing vulnerabilities, creating contract fails with NOT FOUND")
      void withMissingVulnerabilitiesCreateContractFailsWithNOTFOUND() throws Exception {
        Set<Domain> domains =
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setVulnerabilityIds(List.of(UUID.randomUUID().toString()));
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(domains);
        input.setContent("{\"fields\":[]}");

        mvc.perform(
                post(INJECTOR_CONTRACT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
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
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsIds(
            attackPatternComposer.generatedItems.stream().map(AttackPattern::getId).toList());
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(domains);
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
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
                          "injector_contract_injector":"49229430-b5b5-431f-ba5b-f36f599b0144",
                          "injector_contract_attack_patterns":[%s],"injector_contract_vulnerabilities":[],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_injector_type_name":"OpenAEV Implant",
                          "injector_contract_domains":[]
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
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsExternalIds(
            attackPatternComposer.generatedItems.stream()
                .map(ap -> ap.getExternalId().toLowerCase())
                .toList());
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setContent("{\"fields\":[]}");
        input.setDomains(domains);

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
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
                          "injector_contract_injector":"49229430-b5b5-431f-ba5b-f36f599b0144",
                          "injector_contract_attack_patterns":[%s],"injector_contract_vulnerabilities":[],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_injector_type_name":"OpenAEV Implant",
                          "injector_contract_domains":[]
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
        Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setVulnerabilityIds(
            vulnerabilityComposer.generatedItems.stream().map(Vulnerability::getId).toList());
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(domains);
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_ARRAY_ORDER)
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
                          "injector_contract_injector":"49229430-b5b5-431f-ba5b-f36f599b0144",
                          "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[%s],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_injector_type_name":"OpenAEV Implant",
                          "injector_contract_domains":[]
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
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setVulnerabilityExternalIds(
            // force converting the ids to lower case; it must work in case-insensitive mode
            vulnerabilityComposer.generatedItems.stream()
                .map(vuln -> vuln.getExternalId().toLowerCase())
                .toList());
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setDomains(domains);
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .when(Option.IGNORING_ARRAY_ORDER)
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
                          "injector_contract_injector":"49229430-b5b5-431f-ba5b-f36f599b0144",
                          "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[%s],
                          "injector_contract_atomic_testing":true,
                          "injector_contract_import_available":false,"injector_contract_arch":null,
                          "injector_contract_injector_type":"openaev_implant",
                          "injector_contract_injector_type_name":"OpenAEV Implant",
                          "injector_contract_domains":[]
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
                    .contentType(MediaType.APPLICATION_JSON))
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
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Deleting contract fails with NOT FOUND")
      void deleteContractFailsWithNotFound() throws Exception {
        mvc.perform(
                delete(INJECTOR_CONTRACT_URL + "/" + injectorContractInternalId)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Updating contract fails with NOT FOUND")
      void updateContractFailsWithNotFound() throws Exception {
        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        Set<Domain> domains =
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();
        input.setDomains(domains);
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + injectorContractInternalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
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
      mvc.perform(get(INJECTOR_CONTRACT_URL + "//").contentType(MediaType.APPLICATION_JSON))
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
            .hasCauseInstanceOf(PSQLException.class)
            .hasMessageContaining(
                "Key (injector_contract_external_id)=(" + externalId + ") already exists");
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
                    .content(mapper.writeValueAsString(input)))
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
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Updating vulnerability mappings succeeds")
      void updatingVulnerabilitiesMappingsSucceeds() throws Exception {
        for (int i = 0; i < 3; ++i) {
          vulnerabilityComposer
              .forVulnerability(
                  VulnerabilityFixture.createVulnerabilityInput(
                      VulnerabilityFixture.getRandomExternalVulnerabilityId()))
              .persist();
        }
        em.flush();
        em.clear();

        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setVulnerabilityIds(
            vulnerabilityComposer.generatedItems.stream().map(Vulnerability::getId).toList());

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + externalId + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName(
          "Updating vulnerability mappings with non-existing vulnerabilities fail with NOT FOUND")
      void updatingVulnerabilitiesMappingsWithNonExistingVulnerabilitiesFailWithNotFound()
          throws Exception {
        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setVulnerabilityIds(List.of(UUID.randomUUID().toString()));

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + externalId + "/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Fetching by External ID succeeds")
      void fetchByExternalIdSucceeds() throws Exception {
        InjectorContract ic = injectorContractComposer.generatedItems.getFirst();
        String body =
            mvc.perform(
                    get(INJECTOR_CONTRACT_URL + "/" + externalId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(body)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
            .isEqualTo(mapper.writeValueAsString(ic));
      }

      @Nested
      @DisplayName("When deleting an injector contract")
      class WhenDeletingAnInjectorContract {

        @Test
        @DisplayName("Deleting a non custom contract fails")
        void deleteNonCustomContractFails() {
          assertThatThrownBy(
                  () ->
                      mvc.perform(
                              delete(INJECTOR_CONTRACT_URL + "/" + externalId)
                                  .contentType(MediaType.APPLICATION_JSON))
                          .andReturn())
              .hasCauseInstanceOf(IllegalArgumentException.class)
              .hasMessageEndingWith(
                  "This injector contract can't be removed because is not a custom one: "
                      + externalId);
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
                      .contentType(MediaType.APPLICATION_JSON))
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
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();

        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");
        input.setVulnerabilityIds(List.of(vulnWrapper.get().getId()));
        input.setAttackPatternsIds(List.of(attackPatternWrapper.get().getId()));
        input.setDomains(domains);

        String response =
            mvc.perform(
                    put(INJECTOR_CONTRACT_URL + "/" + externalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
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
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();
        String newId = UUID.randomUUID().toString();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(newId);
        input.setExternalId(externalId);
        input.setDomains(domains);
        input.setInjectorId(injectorFixture.getWellKnownOaevImplantInjector().getId());
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths("injector_contract_created_at", "injector_contract_updated_at")
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
                                    "injector_contract_injector":"49229430-b5b5-431f-ba5b-f36f599b0144",
                                    "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[],
                                    "injector_contract_atomic_testing":true,
                                    "injector_contract_import_available":false,"injector_contract_arch":null,
                                    "injector_contract_injector_type":"openaev_implant",
                                    "injector_contract_injector_type_name":"OpenAEV Implant",
                                    "injector_contract_domains":[]
                                  }""",
                    newId));
      }

      @Test
      @DisplayName("Creating contract succeeds")
      void createContractSucceeds() throws Exception {
        Set<Domain> domains =
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();
        String newId = UUID.randomUUID().toString();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(newId);
        input.setExternalId(externalId);
        input.setDomains(domains);
        input.setInjectorId(injectorFixture.getWellKnownEmailInjector(false).getId());
        input.setContent("{\"fields\":[]}");

        String response =
            mvc.perform(
                    post(INJECTOR_CONTRACT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .whenIgnoringPaths(
                "injector_contract_created_at",
                "injector_contract_updated_at",
                "injector_contract_domains[*].domain_created_at",
                "injector_contract_domains[*].domain_updated_at",
                "injector_contract_domains[*].domain_id",
                "injector_contract_domains[*].listened")
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
                                      "injector_contract_injector":"41b4dd55-5bd1-4614-98cd-9e3770753306",
                                      "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[],
                                      "injector_contract_atomic_testing":true,
                                      "injector_contract_import_available":false,"injector_contract_arch":null,
                                      "injector_contract_injector_type":"openaev_email",
                                      "injector_contract_injector_type_name":"Email",
                                      "injector_contract_domains":[{domain_name: "To classify", domain_color: "#FFFFFF"}]
                                    }""",
                    newId));
      }

      @Test
      @DisplayName("Fetching by External ID fails with NOT FOUND")
      void fetchByExternalIdFailsWithNotFound() throws Exception {
        mvc.perform(
                get(INJECTOR_CONTRACT_URL + "/" + externalId)
                    .contentType(MediaType.APPLICATION_JSON))
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
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Deleting contract fails with NOT FOUND")
      void deleteContractFailsWithNotFound() throws Exception {
        mvc.perform(
                delete(INJECTOR_CONTRACT_URL + "/" + externalId)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Updating contract fails with NOT FOUND")
      void updateContractFailsWithNotFound() throws Exception {
        Set<Domain> domains =
            domainComposer
                .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
                .persist()
                .getSet();
        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setDomains(domains);
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");

        mvc.perform(
                put(INJECTOR_CONTRACT_URL + "/" + externalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
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
              "injector_contract_injector",
              injectorFixture.getWellKnownOaevImplantInjector().getId(),
              Filters.FilterOperator.eq);

      String response =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(input)))
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
              "injector_contract_injector",
              injectorFixture.getWellKnownOaevImplantInjector().getId(),
              Filters.FilterOperator.eq);
      input.setIncludeFullDetails(false);

      String response =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(input)))
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
      WITH_ACCESS_PAYLOADS,
      WITH_OBSERVER_GRANT
    }

    private UserComposer.Composer createTestUser(UserType userType) {
      return switch (userType) {
        case NO_GROUPS ->
            userComposer.forUser(
                UserFixture.getUser("NoGroups", "User", UUID.randomUUID() + "@unittests.invalid"));
        case ADMIN ->
            userComposer.forUser(
                UserFixture.getAdminUser(
                    "Admin", "User", UUID.randomUUID() + "@unittests.invalid"));
        case WITH_BYPASS -> {
          GroupComposer.Composer bypassGroup =
              groupComposer
                  .forGroup(GroupFixture.createGroup())
                  .withRole(
                      roleComposer.forRole(
                          RoleFixture.getRole(new HashSet<>(Set.of(Capability.BYPASS)))));

          yield userComposer
              .forUser(
                  UserFixture.getUser("Bypass", "User", UUID.randomUUID() + "@unittests.invalid"))
              .withGroup(bypassGroup);
        }
        case WITH_ACCESS_PAYLOADS -> {
          GroupComposer.Composer payloadsGroup =
              groupComposer
                  .forGroup(GroupFixture.createGroup())
                  .withRole(
                      roleComposer.forRole(
                          RoleFixture.getRole(new HashSet<>(Set.of(Capability.ACCESS_PAYLOADS)))));

          yield userComposer
              .forUser(
                  UserFixture.getUser(
                      "AccessPayloads", "User", UUID.randomUUID() + "@unittests.invalid"))
              .withGroup(payloadsGroup);
        }
        case WITH_OBSERVER_GRANT -> {
          Grant grant = new Grant();
          grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.PAYLOAD);
          grant.setName(Grant.GRANT_TYPE.OBSERVER);
          grant.setResourceId(testPayload.getId());
          GroupComposer.Composer observerGroup =
              groupComposer
                  .forGroup(GroupFixture.createGroup())
                  .withRole(roleComposer.forRole(RoleFixture.getRole(new HashSet<>())))
                  .withGrant(grantComposer.forGrant(grant));

          yield userComposer
              .forUser(
                  UserFixture.getUser("Observer", "User", UUID.randomUUID() + "@unittests.invalid"))
              .withGroup(observerGroup);
        }
        default -> throw new IllegalArgumentException("Unknown user type: " + userType);
      };
    }

    private Payload testPayload;
    private int preExistingContractsCount;

    private void createStaticInjectorContract(boolean addPayload) {
      Set<Domain> domains =
          domainComposer
              .forDomain(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null))
              .persist()
              .getSet();

      InjectorContractComposer.Composer icComposer =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector());
      if (addPayload) {
        icComposer.withPayload(
            payloadComposer.forPayload(PayloadFixture.createDefaultCommand(domains)));
      }
      InjectorContract ic = icComposer.persist().get();
      if (addPayload) {
        testPayload = ic.getPayload();
      }
      em.flush();
      em.clear();
    }

    @BeforeEach
    void setUp() {
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
              false, // shouldSeeAllContracts
              false // shouldSeeContractsWithPayload
              ),
          Arguments.of(
              "Admin user",
              UserType.ADMIN,
              true, // Admin sees all
              true),
          Arguments.of(
              "User with BYPASS capability",
              UserType.WITH_BYPASS,
              true, // BYPASS users should see all
              true),
          Arguments.of(
              "User with ACCESS_PAYLOADS capability",
              UserType.WITH_ACCESS_PAYLOADS,
              true, // ACCESS_PAYLOADS users should see all payload-related contracts
              true),
          Arguments.of(
              "User with OBSERVER grant on payload",
              UserType.WITH_OBSERVER_GRANT,
              false, // Doesn't see all contracts
              true // But can see contracts with the specific granted payload
              ));
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("userTestCases")
    @DisplayName("GET /injector-contracts - Test access control for different user types")
    void testGetInjectContracts(
        String testCase,
        UserType userType,
        boolean shouldSeeAllContracts,
        boolean shouldSeeContractsWithPayload)
        throws Exception {

      // Create test user based on type
      User testUser = createTestUser(userType).persist().get();

      Authentication auth = buildAuthenticationToken(testUser);

      // Perform the request with the test user context
      ResultActions result =
          mvc.perform(
                  get(INJECTOR_CONTRACT_URL)
                      .with(authentication(auth))
                      .contentType(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().is(HttpStatus.SC_OK));

      // Verify the response based on user permissions
      if (shouldSeeAllContracts || shouldSeeContractsWithPayload) {
        // Admin, BYPASS, ACCESS_PAYLOADS users see everything
        // User with OBSERVER grant sees contracts without payload + the specific contract with
        // granted payload
        // That's preExistingContractsCount + 2 (without payload) + 1 (with granted payload) =
        // preExistingContractsCount + 3
        result.andExpect(jsonPath("$", hasSize(equalTo(preExistingContractsCount + 3))));
      } else {
        // User with no groups only sees contracts without payload
        // That's preExistingContractsCount + 2 (only the ones without payload)
        result.andExpect(jsonPath("$", hasSize(equalTo(preExistingContractsCount + 2))));
      }
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("userTestCases")
    @DisplayName(
        "POST /injector-contracts/search without full details - Test search access control for different user types")
    void testSearchInjectorContracts(
        String testCase,
        UserType userType,
        boolean shouldSeeAllContracts,
        boolean shouldSeeContractsWithPayload)
        throws Exception {

      // Create test user based on type
      User testUser = createTestUser(userType).persist().get();

      Authentication auth = buildAuthenticationToken(testUser);

      // Create search input
      InjectorContractSearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setIncludeFullDetails(false);

      ResultActions result =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .with(authentication(auth))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(searchPaginationInput)))
              .andExpect(status().is(HttpStatus.SC_OK));

      // Verify pagination response
      result.andExpect(jsonPath("$.totalElements").exists());
      result.andExpect(jsonPath("$.content").isArray());

      if (shouldSeeAllContracts || shouldSeeContractsWithPayload) {
        // Should see at least contracts without payload
        result.andExpect(jsonPath("$.totalElements", equalTo(preExistingContractsCount + 3)));
      } else {
        // Should only see contracts without payload
        result.andExpect(jsonPath("$.totalElements", equalTo(preExistingContractsCount + 2)));
      }
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("userTestCases")
    @DisplayName(
        "POST /injector-contracts/search with full details - Test search access control for different user types")
    void testSearchInjectorContractsWithFullDetails(
        String testCase,
        UserType userType,
        boolean shouldSeeAllContracts,
        boolean shouldSeeContractsWithPayload)
        throws Exception {

      // Create test user based on type
      User testUser = createTestUser(userType).persist().get();

      Authentication auth = buildAuthenticationToken(testUser);

      // Create search input
      InjectorContractSearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setIncludeFullDetails(true);

      ResultActions result =
          mvc.perform(
                  post(INJECTOR_CONTRACT_URL + "/search")
                      .with(authentication(auth))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(searchPaginationInput)))
              .andExpect(status().is(HttpStatus.SC_OK));

      // Verify pagination response with full details
      result.andExpect(jsonPath("$.totalElements").exists());
      result.andExpect(jsonPath("$.content").isArray());

      // When full details are requested, verify additional fields are present
      if (result.andReturn().getResponse().getContentAsString().contains("content")) {
        result.andExpect(jsonPath("$.content[0].injector_contract_content").exists());
      }

      if (shouldSeeAllContracts) {
        result.andExpect(jsonPath("$.totalElements", equalTo(preExistingContractsCount + 3)));
      } else if (shouldSeeContractsWithPayload) {
        result.andExpect(jsonPath("$.totalElements", equalTo(preExistingContractsCount + 3)));
      } else {
        // Should only see contracts without payload
        result.andExpect(jsonPath("$.totalElements", equalTo(preExistingContractsCount + 2)));
      }
    }
  }
}
