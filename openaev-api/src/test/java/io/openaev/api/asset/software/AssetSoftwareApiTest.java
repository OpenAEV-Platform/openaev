package io.openaev.api.asset.software;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.asset.software.dto.AssetSoftwareInput;
import io.openaev.database.model.Endpoint;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** {@code /api/assets/{id}/software}: software inventory per asset and SBOM import. */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Asset software API")
class AssetSoftwareApiTest extends IntegrationTest {

  private static final String SOFTWARE_URI = "/api/assets/{assetId}/software";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private EndpointComposer endpointComposer;
  @PersistenceContext private EntityManager entityManager;

  private final ObjectMapper mapper = new ObjectMapper();
  private JdbcTemplate jdbc;
  private Endpoint host;
  private Endpoint other;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    endpointComposer.reset();
    host = endpoint("software-host-");
    other = endpoint("software-other-");
    entityManager.flush();
  }

  @Test
  @DisplayName("given a CycloneDX SBOM, should store components with their parent, once")
  void given_cycloneDx_should_storeComponentsWithParent() throws Exception {
    // Act
    importSbom(host.getId(), SbomParserTest.CYCLONEDX)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(3))
        .andExpect(jsonPath("$.updated").value(0))
        .andExpect(jsonPath("$.unknown_versions").value(1));
    importSbom(host.getId(), SbomParserTest.CYCLONEDX)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.updated").value(3));

    // Assert
    assertThat(storedSoftware(host.getId())).isEqualTo(3);
    JsonNode software = list(host.getId());
    JsonNode image = byProduct(software, "ingress-nginx-controller");
    JsonNode nginx = byProduct(software, "nginx");
    JsonNode zlib = byProduct(software, "zlib");
    assertThat(nginx.path("asset_software_parent_id").asText())
        .isEqualTo(image.path("asset_software_id").asText());
    assertThat(zlib.path("asset_software_parent_id").asText())
        .isEqualTo(image.path("asset_software_id").asText());
    assertThat(nginx.path("asset_software_version_state").asText()).isEqualTo("KNOWN");
    assertThat(nginx.path("asset_software_source").asText()).isEqualTo("SBOM");
    assertThat(zlib.path("asset_software_version_state").asText()).isEqualTo("UNKNOWN");
    assertThat(zlib.path("asset_software_version").asText(null) == null)
        .as("an unknown version is null, never an empty string")
        .isTrue();
  }

  @Test
  @DisplayName("given an SPDX SBOM, should store packages and the CONTAINS parent")
  void given_spdx_should_storePackagesAndParent() throws Exception {
    importSbom(host.getId(), SbomParserTest.SPDX).andExpect(status().isOk());

    JsonNode software = list(host.getId());
    assertThat(software.size()).isEqualTo(2);
    assertThat(byProduct(software, "nginx").path("asset_software_parent_id").asText())
        .isEqualTo(byProduct(software, "web-image").path("asset_software_id").asText());
  }

  @Test
  @DisplayName("given software on two assets, should find the assets that run it")
  void given_softwareOnTwoAssets_should_findTheAssets() throws Exception {
    // Arrange
    importSbom(host.getId(), SbomParserTest.CYCLONEDX).andExpect(status().isOk());
    add(other.getId(), new AssetSoftwareInput("F5", "nginx", "1.25.3", null, null, null))
        .andExpect(status().isOk());

    // Act and assert
    mvc.perform(get("/api/assets/software").param("purl", "pkg:generic/nginx@1.27.1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].asset_id").value(host.getId()));
    mvc.perform(get("/api/assets/software").param("purl", "pkg:generic/nginx"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/assets/software").param("product", "NGINX"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    mvc.perform(get("/api/assets/software")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("given a component without a version, should store it as unknown")
  void given_noVersion_should_storeUnknown() throws Exception {
    add(host.getId(), new AssetSoftwareInput("Apache", "httpd", "  ", null, null, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_software_version_state").value("UNKNOWN"))
        .andExpect(jsonPath("$.asset_software_source").value("API"));
    JsonNode httpd = byProduct(list(host.getId()), "httpd");
    assertThat(httpd.path("asset_software_version").asText(null)).isNull();
  }

  @Test
  @DisplayName("given a bundling component, should delete it and keep its children unparented")
  void given_parent_should_deleteAndUnparentChildren() throws Exception {
    // Arrange
    importSbom(host.getId(), SbomParserTest.CYCLONEDX).andExpect(status().isOk());
    String imageId =
        byProduct(list(host.getId()), "ingress-nginx-controller")
            .path("asset_software_id")
            .asText();

    // Act
    mvc.perform(delete(SOFTWARE_URI + "/{softwareId}", host.getId(), imageId).with(csrf()))
        .andExpect(status().isOk());

    // Assert
    entityManager.flush();
    JsonNode software = list(host.getId());
    assertThat(software.size()).isEqualTo(2);
    assertThat(byProduct(software, "nginx").path("asset_software_parent_id").asText(null) == null)
        .isTrue();
    mvc.perform(delete(SOFTWARE_URI + "/{softwareId}", other.getId(), imageId).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given an unknown asset or a document that is not an SBOM, should refuse")
  void given_badInput_should_refuse() throws Exception {
    importSbom(UUID.randomUUID().toString(), SbomParserTest.CYCLONEDX)
        .andExpect(status().isNotFound());
    importSbom(host.getId(), "{\"name\": \"x\"}").andExpect(status().isBadRequest());
    assertThat(storedSoftware(host.getId())).isZero();
  }

  private Endpoint endpoint(String prefix) {
    return endpointComposer
        .forEndpoint(EndpointFixture.createEndpoint(prefix + UUID.randomUUID()))
        .persist()
        .get();
  }

  private ResultActions importSbom(String assetId, String sbom) throws Exception {
    return mvc.perform(
        post(SOFTWARE_URI + "/sbom", assetId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(sbom));
  }

  private ResultActions add(String assetId, AssetSoftwareInput input) throws Exception {
    return mvc.perform(
        post(SOFTWARE_URI, assetId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(input)));
  }

  private JsonNode list(String assetId) throws Exception {
    entityManager.flush();
    String body =
        mvc.perform(get(SOFTWARE_URI, assetId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  private static JsonNode byProduct(JsonNode software, String product) {
    for (JsonNode s : software) {
      if (product.equals(s.path("asset_software_product").asText())) {
        return s;
      }
    }
    throw new AssertionError("no software " + product + " in " + software);
  }

  private int storedSoftware(String assetId) {
    entityManager.flush();
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM asset_software WHERE asset_id = ?", Integer.class, assetId);
    return count == null ? 0 : count;
  }
}
