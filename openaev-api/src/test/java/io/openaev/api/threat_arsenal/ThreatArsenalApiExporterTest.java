package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectorContract;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class ThreatArsenalApiExporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DomainComposer domainComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant();
    injectorContractComposer.reset();
    domainComposer.reset();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Export Threat Arsenal Action")
  class ExportThreatArsenalAction {

    @Test
    @DisplayName("Exporting a payload-based action should return a ZIP JSON:API document")
    void given_payloadBasedActionId_should_exportZipJsonApiDocument() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mockMvc
              .perform(
                  post(THREAT_ARSENAL_URL)
                      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String actionId = JsonPath.read(createResponse, "$.injector_contract_id");

      // Act
      byte[] zipBytes =
          mockMvc
              .perform(get(THREAT_ARSENAL_URL + "/" + actionId + "/export"))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      // Assert
      assertThat(zipBytes).isNotEmpty();

      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
        ZipEntry entry;
        JsonNode document = null;

        while ((entry = zis.getNextEntry()) != null) {
          if (entry.getName().endsWith(".json") && !"meta.json".equals(entry.getName())) {
            document = objectMapper.readTree(zis.readAllBytes());
            break;
          }
        }

        assertThat(document).isNotNull();
        assertThat(document.at("/data/type").asText()).isEqualTo("injectors_contracts");
      }
    }

    @Test
    @DisplayName("Exporting an unknown action should return not found")
    void given_unknownActionId_should_returnNotFound() throws Exception {
      // Arrange
      String unknownActionId = UUID.randomUUID().toString();

      // Act / Assert
      mockMvc
          .perform(get(THREAT_ARSENAL_URL + "/" + unknownActionId + "/export"))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Exporting a non-payload injector contract should return not found")
    void given_nonPayloadInjectorContract_should_returnNotFound() throws Exception {
      // Arrange
      InjectorContract nonPayloadContract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownEmailInjector(false))
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()).persist())
              .persist()
              .get();

      // Act / Assert
      mockMvc
          .perform(get(THREAT_ARSENAL_URL + "/" + nonPayloadContract.getId() + "/export"))
          .andExpect(status().isNotFound());
    }
  }
}
