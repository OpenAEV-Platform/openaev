package io.openaev.api.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.utils.constants.Constants.IMPORTED_OBJECT_NAME_SUFFIX;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Payload;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Payload api importer tests")
class PayloadApiImporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ZipJsonService<Payload> zipJsonService;

  @Test
  @DisplayName("Import a payload returns complete entity")
  void import_payload_returns_payload_with_relationship() throws Exception {
    // -- PREPARE --
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("payload_type", "Command");
    attributes.put("command_executor", "psh");
    attributes.put("command_content", "echo \"toto\"");
    attributes.put("payload_name", "Echo");
    attributes.put("payload_description", "");
    attributes.put("payload_platforms", new String[] {"Windows"});
    attributes.put("payload_source", "MANUAL");
    attributes.put("payload_expectations", new String[] {"VULNERABILITY"});
    attributes.put("payload_status", "VERIFIED");
    attributes.put("payload_execution_arch", "ALL_ARCHITECTURES");

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(null, "command", attributes, emptyMap()), emptyList());

    byte[] zip = zipJsonService.writeZip(document, emptyMap());
    MockMultipartFile zipFile =
        new MockMultipartFile("file", "payload.zip", "application/zip", zip);

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(multipart(PAYLOAD_URI + "/import").file(zipFile))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);

    JsonNode json = new ObjectMapper().readTree(response);

    // Payload
    assertEquals("command", json.at("/data/type").asText());
    assertEquals(
        "Echo " + IMPORTED_OBJECT_NAME_SUFFIX, json.at("/data/attributes/payload_name").asText());
    assertEquals("psh", json.at("/data/attributes/command_executor").asText());
    assertEquals("echo \"toto\"", json.at("/data/attributes/command_content").asText());
  }
}
