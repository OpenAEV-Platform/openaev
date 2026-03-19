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
import java.util.List;
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
        "Echo" + IMPORTED_OBJECT_NAME_SUFFIX, json.at("/data/attributes/payload_name").asText());
    assertEquals("psh", json.at("/data/attributes/command_executor").asText());
    assertEquals("echo \"toto\"", json.at("/data/attributes/command_content").asText());
  }

  @Test
  @DisplayName("Import a payload returns complete entity with all array fields")
  void importPayloadReturnsPayloadWithAllArrayFields() throws Exception {
    // -- PREPARE --
    // payload_arguments and payload_prerequisites must be arrays of objects,
    // matching the PayloadArgument / PayloadPrerequisite model schema.
    Map<String, Object> argument1 =
        Map.of("type", "text", "key", "target_host", "default_value", "localhost");
    Map<String, Object> argument2 = Map.of("type", "text", "key", "port", "default_value", "8080");
    Map<String, Object> prerequisite1 =
        Map.of("executor", "sh", "get_command", "which curl", "check_command", "curl --version");

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("payload_type", "Command");
    attributes.put("command_executor", "psh");
    attributes.put("command_content", "echo \"toto\"");
    attributes.put("payload_name", "Echo");
    attributes.put("payload_description", "");
    attributes.put("payload_platforms", new String[] {"Windows"});
    attributes.put("payload_arguments", List.of(argument1, argument2));
    attributes.put("payload_prerequisites", List.of(prerequisite1));
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
    assertEquals("command", json.at("/data/type").asText());
    assertEquals(
        "Echo" + IMPORTED_OBJECT_NAME_SUFFIX, json.at("/data/attributes/payload_name").asText());
    assertEquals("psh", json.at("/data/attributes/command_executor").asText());
    assertEquals("echo \"toto\"", json.at("/data/attributes/command_content").asText());
    assertEquals(1, json.at("/data/attributes/payload_platforms").size());

    // Assert argument field values, not just array size
    JsonNode args = json.at("/data/attributes/payload_arguments");
    assertEquals(2, args.size());
    assertEquals("text", args.get(0).get("type").asText());
    assertEquals("target_host", args.get(0).get("key").asText());
    assertEquals("localhost", args.get(0).get("default_value").asText());
    assertEquals("port", args.get(1).get("key").asText());
    assertEquals("8080", args.get(1).get("default_value").asText());

    // Assert prerequisite field values, not just array size
    JsonNode prereqs = json.at("/data/attributes/payload_prerequisites");
    assertEquals(1, prereqs.size());
    assertEquals("sh", prereqs.get(0).get("executor").asText());
    assertEquals("which curl", prereqs.get(0).get("get_command").asText());
    assertEquals("curl --version", prereqs.get(0).get("check_command").asText());
  }

  @Test
  @DisplayName("Import payload with empty array fields")
  void importPayloadWithEmptyArrayFields() throws Exception {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("payload_type", "Command");
    attributes.put("command_executor", "psh");
    attributes.put("command_content", "echo \"toto\"");
    attributes.put("payload_name", "Echo");
    attributes.put("payload_description", "");
    attributes.put("payload_platforms", new String[] {}); // empty array
    attributes.put("payload_arguments", new String[] {}); // empty array
    attributes.put("payload_prerequisites", new String[] {}); // empty array
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

    String response =
        mockMvc
            .perform(multipart(PAYLOAD_URI + "/import").file(zipFile))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(response);
    JsonNode json = new ObjectMapper().readTree(response);
    assertEquals(0, json.at("/data/attributes/payload_platforms").size());
    assertEquals(0, json.at("/data/attributes/payload_arguments").size());
    assertEquals(0, json.at("/data/attributes/payload_prerequisites").size());
  }

  @Test
  @DisplayName("Import payload with null (missing) array fields")
  void importPayloadWithNullArrayFields() throws Exception {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("payload_type", "Command");
    attributes.put("command_executor", "psh");
    attributes.put("command_content", "echo \"toto\"");
    attributes.put("payload_name", "Echo");
    attributes.put("payload_description", "");
    // omit payload_platforms, payload_arguments, payload_prerequisites
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

    String response =
        mockMvc
            .perform(multipart(PAYLOAD_URI + "/import").file(zipFile))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(response);
    JsonNode json = new ObjectMapper().readTree(response);
    assertEquals(0, json.at("/data/attributes/payload_platforms").size());
    assertEquals(0, json.at("/data/attributes/payload_arguments").size());
    assertEquals(0, json.at("/data/attributes/payload_prerequisites").size());
  }
}
