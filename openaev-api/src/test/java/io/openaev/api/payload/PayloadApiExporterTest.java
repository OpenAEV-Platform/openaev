package io.openaev.api.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.utils.fixtures.PayloadFixture.COMMAND_PAYLOAD_NAME;
import static io.openaev.utils.fixtures.PayloadFixture.createDefaultCommand;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static io.openaev.utilstest.ZipUtils.convertToJson;
import static io.openaev.utilstest.ZipUtils.extractAllFilesFromZip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Domain;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Payload api exporter tests")
class PayloadApiExporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private TagComposer tagComposer;

  PayloadComposer.Composer createPayloadComposer() {
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
    return this.payloadComposer
        .forPayload(createDefaultCommand(domains))
        .withTag(tagComposer.forTag(getTagWithText("malware")))
        .persist();
  }

  @Test
  @DisplayName("Export a payload returns entity")
  void export_payload_returns_payload_with_relationship() throws Exception {
    // -- PREPARE --
    PayloadComposer.Composer wrapper = createPayloadComposer();

    // -- EXECUTE --
    byte[] response =
        mockMvc
            .perform(get(PAYLOAD_URI + "/" + wrapper.get().getId() + "/export"))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    // -- ASSERT --
    assertNotNull(response);
    Map<String, byte[]> files = extractAllFilesFromZip(response);
    Map<String, String> jsonFiles = convertToJson(files);

    // Payload
    String payloadString =
        jsonFiles.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("command"))
            .map(Map.Entry::getValue)
            .findFirst()
            .get();
    JsonNode payloadJson = new ObjectMapper().readTree(payloadString);
    assertEquals("command", payloadJson.at("/data/type").asText());
    assertEquals(COMMAND_PAYLOAD_NAME, payloadJson.at("/data/attributes/payload_name").asText());
    assertEquals(
        "malware", payloadJson.at("/included").get(0).get("attributes").get("tag_name").asText());
  }
}
