package io.openaev.rest.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class ThreatArsenalApiImporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DomainComposer domainComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @BeforeEach
  void beforeEach() throws Exception {
    new Manager(List.of(openaevInjectorIntegrationFactory)).monitorIntegrations();
    domainComposer.reset();
    injectorContractComposer.reset();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Import Threat Arsenal Action")
  class ImportThreatArsenalAction {

    @Test
    @DisplayName(
        "Importing an injector-contract export should create a new action with imported naming")
    void given_injectorContractExport_should_importThreatArsenalAction() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mockMvc
              .perform(
                  post(THREAT_ARSENAL_URL)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String originalActionId = JsonPath.read(createResponse, "$.injector_contract_id");
      String originalPayloadName = createInput.name();

      byte[] exportedZip =
          mockMvc
              .perform(get(THREAT_ARSENAL_URL + "/" + originalActionId + "/export"))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      MockMultipartFile zipFile =
          new MockMultipartFile("file", "threat-arsenal.zip", "application/zip", exportedZip);

      // Act
      String importResponse =
          mockMvc
              .perform(multipart(THREAT_ARSENAL_URL + "/import").file(zipFile))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String importedActionId = JsonPath.read(importResponse, "$.injector_contract_id");
      String importedPayloadId = JsonPath.read(importResponse, "$.action_payload.payload_id");
      Map<String, String> importedLabels = JsonPath.read(importResponse, "$.action_labels");
      Payload importedPayload = payloadRepository.findById(importedPayloadId).orElseThrow();

      assertThat(importedActionId).isNotEqualTo(originalActionId);
      assertThat(importedPayload.getName()).isEqualTo(originalPayloadName + " (Import)");
      assertThat(importedLabels).isNotEmpty();
      assertThat(importedLabels.values()).allMatch(label -> label.endsWith(" (Import)"));
      assertThat(payloadRepository.findById(importedPayloadId)).isPresent();
      assertThat(injectorContractRepository.findById(importedActionId)).isPresent();
    }

    @Test
    @DisplayName("Importing a payload export should use the legacy payload import path")
    void given_payloadExport_should_importThreatArsenalActionFromPayload() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mockMvc
              .perform(
                  post(THREAT_ARSENAL_URL)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String originalActionId = JsonPath.read(createResponse, "$.injector_contract_id");
      String originalPayloadId = JsonPath.read(createResponse, "$.action_payload.payload_id");
      String originalPayloadName = createInput.name();

      byte[] exportedZip =
          mockMvc
              .perform(get(PAYLOAD_URI + "/" + originalPayloadId + "/export"))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      MockMultipartFile zipFile =
          new MockMultipartFile("file", "payload.zip", "application/zip", exportedZip);

      // Act
      String importResponse =
          mockMvc
              .perform(multipart(THREAT_ARSENAL_URL + "/import").file(zipFile))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String importedActionId = JsonPath.read(importResponse, "$.injector_contract_id");
      String importedPayloadId = JsonPath.read(importResponse, "$.action_payload.payload_id");

      assertThat(importedActionId).isNotEqualTo(originalActionId);
      assertThat(importedPayloadId).isNotEqualTo(originalPayloadId);

      Payload importedPayload = payloadRepository.findById(importedPayloadId).orElseThrow();
      assertThat(importedPayload.getName()).isEqualTo(originalPayloadName + " (Import)");
      assertThat(injectorContractRepository.findInjectorContractByPayload(importedPayload))
          .map(InjectorContract::getId)
          .contains(importedActionId);
    }
  }
}
