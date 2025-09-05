package io.openbas.rest.injector_contract;

import static io.openbas.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.Cve;
import io.openbas.database.model.Filters;
import io.openbas.database.model.InjectorContract;
import io.openbas.rest.injector_contract.form.InjectorContractAddInput;
import io.openbas.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openbas.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openbas.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openbas.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openbas.rest.injector_contract.output.InjectorContractFullOutput;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.AttackPatternComposer;
import io.openbas.utils.fixtures.composers.CveComposer;
import io.openbas.utils.fixtures.composers.InjectorContractComposer;
import io.openbas.utils.fixtures.files.AttackPatternFixture;
import io.openbas.utils.mockUser.WithMockAdminUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.BatchUpdateException;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockAdminUser
@DisplayName("Injector Contract API tests")
public class InjectorContractApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private EntityManager em;
  @Autowired private ObjectMapper mapper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private CveComposer cveComposer;

  @BeforeEach
  public void setup() {
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    cveComposer.reset();
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
            .withInjector(injectorFixture.getWellKnownObasImplantInjector())
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
          cveComposer.forCve(CveFixture.createDefaultCve()).persist();
        }
        em.flush();
        em.clear();

        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setVulnerabilityIds(cveComposer.generatedItems.stream().map(Cve::getId).toList());

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
                  .withInjector(injectorFixture.getWellKnownObasImplantInjector())
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
        CveComposer.Composer vulnWrapper =
            cveComposer.forCve(CveFixture.createDefaultCve()).persist();
        AttackPatternComposer.Composer attackPatternWrapper =
            attackPatternComposer
                .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
                .persist();
        em.flush();

        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");
        input.setVulnerabilityIds(List.of(vulnWrapper.get().getId()));
        input.setAttackPatternsIds(List.of(attackPatternWrapper.get().getId()));

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
    }

    @Nested
    @DisplayName("When injector contract does not already exists")
    class WhenInjectorContractDoesNotAlreadyExists {
      private final String injectorContractInternalId = UUID.randomUUID().toString();

      @Test
      @DisplayName("Without attack patterns, creating contract succeeds")
      void createContractSucceeds() throws Exception {
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setInjectorId(injectorFixture.getWellKnownObasImplantInjector().getId());
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
                      "injector_contract_injector_type":"openbas_implant",
                      "injector_contract_injector_type_name":"OpenBAS Implant"
                    }
                    """,
                    injectorContractInternalId));
      }

      @Test
      @DisplayName("With missing attack patterns, creating contract fails with NOT FOUND")
      void withMissingAttackPatternsCreateContractFailsWithNOTFOUND() throws Exception {
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsIds(List.of(UUID.randomUUID().toString()));
        input.setInjectorId(injectorFixture.getWellKnownObasImplantInjector().getId());
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
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setVulnerabilityIds(List.of(UUID.randomUUID().toString()));
        input.setInjectorId(injectorFixture.getWellKnownObasImplantInjector().getId());
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

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsIds(
            attackPatternComposer.generatedItems.stream().map(AttackPattern::getId).toList());
        input.setInjectorId(injectorFixture.getWellKnownObasImplantInjector().getId());
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
                      "injector_contract_injector_type":"openbas_implant",
                      "injector_contract_injector_type_name":"OpenBAS Implant"
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

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setAttackPatternsExternalIds(
            attackPatternComposer.generatedItems.stream()
                .map(AttackPattern::getExternalId)
                .toList());
        input.setInjectorId(injectorFixture.getWellKnownObasImplantInjector().getId());
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
                      "injector_contract_injector_type":"openbas_implant",
                      "injector_contract_injector_type_name":"OpenBAS Implant"
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
          cveComposer.forCve(CveFixture.createDefaultCve()).persist();
        }
        em.flush();
        em.clear();

        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(injectorContractInternalId);
        input.setVulnerabilityIds(cveComposer.generatedItems.stream().map(Cve::getId).toList());
        input.setInjectorId(injectorFixture.getWellKnownObasImplantInjector().getId());
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
                                  "injector_contract_attack_patterns":[],"injector_contract_vulnerabilities":[%s],
                                  "injector_contract_atomic_testing":true,
                                  "injector_contract_import_available":false,"injector_contract_arch":null,
                                  "injector_contract_injector_type":"openbas_implant",
                                  "injector_contract_injector_type_name":"OpenBAS Implant"
                                }
                                """,
                    injectorContractInternalId,
                    String.join(
                        ",",
                        cveComposer.generatedItems.stream()
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
            .withInjector(injectorFixture.getWellKnownObasImplantInjector())
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
          cveComposer.forCve(CveFixture.createDefaultCve()).persist();
        }
        em.flush();
        em.clear();

        InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
        input.setVulnerabilityIds(cveComposer.generatedItems.stream().map(Cve::getId).toList());

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
                  .withInjector(injectorFixture.getWellKnownObasImplantInjector())
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
        CveComposer.Composer vulnWrapper =
            cveComposer.forCve(CveFixture.createDefaultCve()).persist();
        AttackPatternComposer.Composer attackPatternWrapper =
            attackPatternComposer
                .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
                .persist();
        em.flush();

        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[], \"arbitrary_field\": \"test\"}");
        input.setVulnerabilityIds(List.of(vulnWrapper.get().getId()));
        input.setAttackPatternsIds(List.of(attackPatternWrapper.get().getId()));

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
      @DisplayName("Creating contract succeeds")
      void createContractSucceeds() throws Exception {
        String newId = UUID.randomUUID().toString();
        InjectorContractAddInput input = new InjectorContractAddInput();
        input.setId(newId);
        input.setExternalId(externalId);
        input.setInjectorId(injectorFixture.getWellKnownObasImplantInjector().getId());
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
                      "injector_contract_injector_type":"openbas_implant",
                      "injector_contract_injector_type_name":"OpenBAS Implant"
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
        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
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
          .withInjector(injectorFixture.getWellKnownObasImplantInjector())
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
              injectorFixture.getWellKnownObasImplantInjector().getId(),
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
              injectorFixture.getWellKnownObasImplantInjector().getId(),
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
}
