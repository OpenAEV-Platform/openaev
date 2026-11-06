package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.CATALOG_URI;
import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.SCENARIO_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.archive;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFile;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,CATALOG")
@DisplayName("Catalogue sync endpoint")
class CatalogSyncApiTest extends IntegrationTest {

  private static final String CATALOG =
      TENANT_CATALOG_URI.replace("{tenantId}", DEFAULT_TENANT_UUID);

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;

  @BeforeEach
  void attachToDefaultTenant() {
    tenantIsolationTestHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
  }

  /** A release under construction: its manifest entries and the files sent with it. */
  private final class Release {
    private final String name;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private final List<Map<String, Object>> payloads = new ArrayList<>();
    private final Map<String, byte[]> files = new LinkedHashMap<>();

    private Release(String name) {
      this.name = name;
    }

    Release scenario(String version, String status, byte[] archive) {
      items.add(scenarioItem(version, status, archive));
      files.put(scenarioFileName(version), archive);
      return this;
    }

    Release payload(String version) {
      byte[] file = payloadFile(PAYLOAD_ID);
      payloads.add(payloadVersion(version, file));
      files.put(payloadFileName(version), file);
      return this;
    }

    @SuppressWarnings("unchecked")
    Release wrongHash() {
      ((Map<String, Object>) items.getLast().get("archive")).put("sha256", "0".repeat(64));
      return this;
    }

    Release file(String fileName, byte[] content) {
      files.put(fileName, content);
      return this;
    }

    Release community(String contributor) {
      items.getLast().put("origin", "community");
      items.getLast().put("contributor", contributor);
      return this;
    }

    ResultActions sync() throws Exception {
      return post(mapper.writeValueAsBytes(manifest(name, items, payloads)), files);
    }

    ResultActions syncWithoutTenant(String... tenantIds) throws Exception {
      MockMultipartHttpServletRequestBuilder request =
          multipart(CATALOG_URI + "/sync")
              .file(
                  new MockMultipartFile(
                      "manifest",
                      "manifest.json",
                      MediaType.APPLICATION_JSON_VALUE,
                      mapper.writeValueAsBytes(manifest(name, items, payloads))));
      files.forEach(
          (fileName, content) ->
              request.file(
                  new MockMultipartFile(
                      "archives", fileName, "application/octet-stream", content)));
      if (tenantIds.length > 0) {
        request.header("X-Tenant-Ids", String.join(",", tenantIds));
      }
      return mvc.perform(request.with(csrf()));
    }
  }

  private Release release(String name) {
    return new Release(name);
  }

  private ResultActions post(byte[] manifest, Map<String, byte[]> files) throws Exception {
    MockMultipartHttpServletRequestBuilder request =
        multipart(CATALOG + "/sync")
            .file(
                new MockMultipartFile(
                    "manifest", "manifest.json", MediaType.APPLICATION_JSON_VALUE, manifest));
    files.forEach(
        (name, content) ->
            request.file(
                new MockMultipartFile("archives", name, "application/octet-stream", content)));
    return mvc.perform(request.with(csrf()));
  }

  private JsonNode body(ResultActions result) throws Exception {
    return mapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private JsonNode list(String type) throws Exception {
    return body(mvc.perform(get(CATALOG + "/" + type)).andExpect(status().isOk()));
  }

  @Nested
  @DisplayName("Applying a release")
  class Applying {

    @Test
    @DisplayName("A new release adds its versions, lists the latest of each, and applies once")
    void given_newRelease_should_addAndListLatestVersions() throws Exception {
      // Arrange
      Release release =
          release("1.261105.0")
              .scenario("1.0.0", "VERIFIED", archive("{\"v\":1}"))
              .scenario("1.1.0", "VERIFIED", archive("{\"v\":2}"))
              .payload("2.0.0");

      // Act
      JsonNode first = body(release.sync().andExpect(status().isOk()));
      JsonNode again = body(release.sync().andExpect(status().isOk()));

      // Assert
      assertThat(first.path("applied").asBoolean()).isTrue();
      assertThat(first.path("added").asInt()).isEqualTo(3);
      assertThat(again.path("applied").asBoolean()).isFalse();
      assertThat(list("scenarios").findValuesAsText("catalog_scenario_version"))
          .containsExactly("1.1.0");
      assertThat(list("payloads").findValuesAsText("catalog_payload_external_id"))
          .containsExactly(PAYLOAD_ID);
    }

    @Test
    @DisplayName("A next release keeps the versions it lists again and removes the others")
    void given_nextRelease_should_keepListedAndRemoveOthers() throws Exception {
      // Arrange
      byte[] archive = archive("{\"v\":1}");
      release("1.261105.0").scenario("1.0.0", "VERIFIED", archive).payload("2.0.0").sync();

      // Act
      JsonNode output =
          body(
              release("1.261106.0")
                  .scenario("1.0.0", "VERIFIED", archive)
                  .sync()
                  .andExpect(status().isOk()));

      // Assert
      assertThat(output.path("unchanged").asInt()).isEqualTo(1);
      assertThat(output.path("added").asInt()).isZero();
      assertThat(list("scenarios").findValuesAsText("catalog_scenario_external_id"))
          .containsExactly(SCENARIO_ID);
      assertThat(list("payloads").findValuesAsText("catalog_payload_external_id")).isEmpty();
    }

    @Test
    @DisplayName("A published version sent again with another content is refused")
    void given_publishedVersionWithOtherContent_should_refuseIt() throws Exception {
      // Arrange
      release("1.261105.0").scenario("1.0.0", "VERIFIED", archive("{\"v\":1}")).sync();

      // Act
      JsonNode output =
          body(
              release("1.261106.0")
                  .scenario("1.0.0", "VERIFIED", archive("{\"v\":2}"))
                  .sync()
                  .andExpect(status().isOk()));

      // Assert
      assertThat(output.path("refused").findValuesAsText("reason"))
          .containsExactly("A published version cannot change its content");
    }

    @Test
    @DisplayName("A deprecated version is received but not listed")
    void given_deprecatedVersion_should_notBeListed() throws Exception {
      // Act
      JsonNode output =
          body(
              release("1.261105.0")
                  .scenario("1.0.0", "DEPRECATED", archive("{\"v\":1}"))
                  .sync()
                  .andExpect(status().isOk()));

      // Assert
      assertThat(output.path("added").asInt()).isEqualTo(1);
      assertThat(list("scenarios").findValuesAsText("catalog_scenario_external_id")).isEmpty();
    }

    @Test
    @DisplayName("A version failing its checks is refused alone, the rest is applied")
    void given_wrongHash_should_refuseTheVersionAlone() throws Exception {
      // Act
      JsonNode output =
          body(
              release("1.261105.0")
                  .scenario("1.0.0", "VERIFIED", archive("{\"v\":1}"))
                  .wrongHash()
                  .payload("2.0.0")
                  .sync()
                  .andExpect(status().isOk()));

      // Assert
      assertThat(output.path("added").asInt()).isEqualTo(1);
      assertThat(output.path("refused").findValuesAsText("reason"))
          .containsExactly("Archive hash does not match the manifest");
      assertThat(list("scenarios").findValuesAsText("catalog_scenario_external_id")).isEmpty();
    }
  }

  @Nested
  @DisplayName("Reading who wrote a scenario")
  class Origin {

    @Test
    @DisplayName("A community scenario is authored by its contributor, a Filigran one by Filigran")
    void given_origin_should_setTheAuthor() throws Exception {
      // Act
      release("1.261105.0")
          .scenario("1.0.0", "VERIFIED", archive("{\"v\":1}"))
          .scenario("1.1.0", "VERIFIED", archive("{\"v\":2}"))
          .community("octocat")
          .sync()
          .andExpect(status().isOk());

      // Assert
      assertThat(
              entityManager
                  .createNativeQuery(
                      "SELECT catalog_item_version || ':' || catalog_item_author FROM catalog_items"
                          + " WHERE catalog_item_external_id = :id")
                  .setParameter("id", SCENARIO_ID)
                  .getResultList())
          .containsExactlyInAnyOrder("1.0.0:Filigran", "1.1.0:octocat");
    }
  }

  @Nested
  @DisplayName("Sending without a tenant in the URL")
  class WithoutTenant {

    @Test
    @DisplayName("A caller in one tenant applies the release in that tenant")
    void given_singleTenantCaller_should_applyInItsTenant() throws Exception {
      // Act & Assert
      release("1.261105.0")
          .scenario("1.0.0", "VERIFIED", archive("{\"v\":1}"))
          .syncWithoutTenant(DEFAULT_TENANT_UUID)
          .andExpect(status().isOk());
      assertThat(list("scenarios").findValuesAsText("catalog_scenario_version"))
          .containsExactly("1.0.0");
    }

    @Test
    @DisplayName("A caller in several tenants is refused instead of writing to the default tenant")
    void given_severalTenants_should_beRefused() throws Exception {
      // Arrange
      String other = tenantIsolationTestHelper.createTenantWithCurrentUser("Catalog other").getId();

      // Act & Assert
      release("1.261105.0")
          .scenario("1.0.0", "VERIFIED", archive("{\"v\":1}"))
          .syncWithoutTenant(DEFAULT_TENANT_UUID, other)
          .andExpect(status().isBadRequest());
      assertThat(list("scenarios").findValuesAsText("catalog_scenario_version")).isEmpty();
    }
  }

  @Nested
  @DisplayName("Refusing a whole release")
  class Refusing {

    @Test
    @DisplayName("A release older than the last one applied is refused")
    void given_olderRelease_should_beRefused() throws Exception {
      // Arrange
      byte[] archive = archive("{\"v\":1}");
      release("1.261105.1").scenario("1.0.0", "VERIFIED", archive).sync();

      // Act & Assert
      release("1.261105.0")
          .scenario("1.0.0", "VERIFIED", archive)
          .sync()
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A release whose name is not a semantic version is refused")
    void given_releaseNameNotSemantic_should_beRefused() throws Exception {
      // Act & Assert
      release("2026.11.05.1")
          .scenario("1.0.0", "VERIFIED", archive("{\"v\":1}"))
          .sync()
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A release sending a file its manifest does not list is refused")
    void given_unlistedFile_should_beRefused() throws Exception {
      // Act & Assert
      release("1.261105.0")
          .scenario("1.0.0", "VERIFIED", archive("{\"v\":1}"))
          .file("extra.zip", archive("{}"))
          .sync()
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A release sending a file larger than its manifest says is refused")
    void given_fileLargerThanListed_should_beRefused() throws Exception {
      // Act & Assert
      release("1.261105.0")
          .payload("2.0.0")
          .file(
              payloadFileName("2.0.0"),
              (new String(payloadFile(PAYLOAD_ID), StandardCharsets.UTF_8) + " ".repeat(64))
                  .getBytes(StandardCharsets.UTF_8))
          .sync()
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An unsupported manifest version is refused")
    void given_unsupportedSchema_should_beRefused() throws Exception {
      // Arrange
      Map<String, Object> manifest = manifest("1.261105.0", List.of(), List.of());
      manifest.put("schema_version", 2);

      // Act & Assert
      post(mapper.writeValueAsBytes(manifest), Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An unreadable manifest is refused")
    void given_unreadableManifest_should_beRefused() throws Exception {
      // Act & Assert
      post("not json".getBytes(StandardCharsets.UTF_8), Map.of())
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A manifest that is null is refused")
    void given_nullManifest_should_beRefused() throws Exception {
      // Act & Assert
      post("null".getBytes(StandardCharsets.UTF_8), Map.of()).andExpect(status().isBadRequest());
    }
  }
}
