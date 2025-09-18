package io.openbas.api.custom_dashboard;

import static io.openbas.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openbas.utils.constants.Constants.IMPORTED_OBJECT_NAME_SUFFIX;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.CustomDashboard;
import io.openbas.jsonapi.JsonApiDocument;
import io.openbas.jsonapi.ResourceObject;
import io.openbas.service.ZipJsonService;
import io.openbas.utils.mockUser.WithMockUser;
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
@DisplayName("Custom dashboard api importer tests")
class CustomDashboardApiImporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ZipJsonService<CustomDashboard> zipJsonService;

  @Test
  @DisplayName("Import a custom dashboard returns complete entity")
  void import_custom_dashboard_with_include_returns_custom_dashboard_with_relationship()
      throws Exception {
    // -- PREPARE --
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("custom_dashboard_name", "Custom dashboard");
    attributes.put("custom_dashboard_description", "A description");
    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(null, "custom_dashboards", attributes, emptyMap()), emptyList());
    byte[] zip = zipJsonService.writeZip(document, emptyMap());
    MockMultipartFile zipFile =
        new MockMultipartFile("file", "custom_dashboard.zip", "application/zip", zip);

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(multipart(CUSTOM_DASHBOARDS_URI + "/import").file(zipFile))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);

    // Custom dashboard
    JsonNode json = new ObjectMapper().readTree(response);
    assertEquals("custom_dashboards", json.at("/data/type").asText());
    assertEquals(
        "Custom dashboard " + IMPORTED_OBJECT_NAME_SUFFIX,
        json.at("/data/attributes/custom_dashboard_name").asText());
    assertEquals(
        "A description", json.at("/data/attributes/custom_dashboard_description").asText());
  }
}
